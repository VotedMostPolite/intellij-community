// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.util.Pair
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.LayoutBuilder
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class BuildItem(val dir: Path, val layout: PluginLayout)

class PluginBuilder(private val builder: DistributionJARsBuilder,
                    val buildContext: BuildContext,
                    private val outDir: Path) {
  private val dirtyPlugins = HashSet<BuildItem>()

  @Synchronized
  fun addDirtyPluginDir(item: BuildItem, reason: Any) {
    if (dirtyPlugins.add(item)) {
      LOG.info("${item.dir.fileName} is changed (at least ${if (reason is Path) outDir.relativize(reason) else reason} is changed)")
    }
  }

  @Synchronized
  private fun getDirtyPluginsAndClear(): Collection<BuildItem> {
    if (dirtyPlugins.isEmpty()) {
      return emptyList()
    }

    val result = dirtyPlugins.toList()
    dirtyPlugins.clear()
    return result
  }

  fun buildChanged(): String {
    val dirtyPlugin = getDirtyPluginsAndClear()
    if (dirtyPlugin.isEmpty()) {
      return "All plugins are up to date"
    }

    val layoutBuilder = LayoutBuilder(buildContext, false)
    for (plugin in dirtyPlugin) {
      try {
        clearDirContent(plugin.dir)
        buildPlugin(plugin, buildContext, builder, layoutBuilder)
      }
      catch (e: Throwable) {
        // put back (that's ok to add already processed plugins - doesn't matter, no need to complicate)
        dirtyPlugin.forEach {
          addDirtyPluginDir(it, "<internal error>")
        }
        throw e
      }
    }
    return "Plugins ${dirtyPlugin.joinToString { it.dir.fileName.toString() }} were updated"
  }
}

fun buildPlugins(@Suppress("SameParameterValue") parallelCount: Int,
                 buildContext: BuildContext,
                 plugins: List<BuildItem>,
                 builder: DistributionJARsBuilder) {
  val executor: Executor = if (parallelCount == 1) {
    Executor(Runnable::run)
  }
  else {
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Building Plugins", parallelCount, false)
  }
  val errorRef = AtomicReference<Throwable>()

  var sharedLayoutBuilder: LayoutBuilder? = null
  for (plugin in plugins) {
    if (errorRef.get() != null) {
      break
    }

    val buildContextForPlugin = if (parallelCount == 1) buildContext else buildContext.forkForParallelTask("Build ${plugin.layout.mainModule}")
    executor.execute(Runnable {
      if (errorRef.get() != null) {
        return@Runnable
      }

      try {
        val layoutBuilder: LayoutBuilder
        if (parallelCount == 1) {
          if (sharedLayoutBuilder == null) {
            sharedLayoutBuilder = LayoutBuilder(buildContext, false)
          }
          layoutBuilder = sharedLayoutBuilder!!
        }
        else {
          layoutBuilder = LayoutBuilder(buildContextForPlugin, false)
        }
        buildPlugin(plugin, buildContextForPlugin, builder, layoutBuilder)
      }
      catch (e: Throwable) {
        if (errorRef.compareAndSet(null, e)) {
          throw e
        }
      }
    })
  }

  if (executor is ExecutorService) {
    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.MINUTES)
  }

  errorRef.get()?.let {
    throw it
  }
}

private fun buildPlugin(plugin: BuildItem,
                        buildContext: BuildContext,
                        builder: DistributionJARsBuilder,
                        layoutBuilder: LayoutBuilder) {
  val mainModule = plugin.layout.mainModule
  if (skippedPluginModules.contains(mainModule)) {
    return
  }

  buildContext.messages.info("Build ${mainModule}")
  val generatedResources = getGeneratedResources(plugin.layout, buildContext)

  if (mainModule != "intellij.platform.builtInHelp") {
    builder.checkOutputOfPluginModules(mainModule, plugin.layout.moduleJars, plugin.layout.moduleExcludes)
  }

  val mapping = ProjectStructureMapping()
  builder.processLayout(layoutBuilder, plugin.layout, plugin.dir.toString(), mapping, true, plugin.layout.moduleJars, generatedResources)
}

private fun getGeneratedResources(plugin: PluginLayout, buildContext: BuildContext): List<Pair<File, String>> {
  if (plugin.resourceGenerators.isEmpty()) {
    return emptyList()
  }

  val generatedResources = ArrayList<Pair<File, String>>(plugin.resourceGenerators.size)
  for (resourceGenerator in plugin.resourceGenerators) {
    if (resourceGenerator.first::class.java.name == "org.jetbrains.intellij.build.sharedIndexes.PreSharedIndexesGenerator") {
      continue
    }

    val resourceFile = resourceGenerator.first.generateResources(buildContext)
    if (resourceFile != null) {
      generatedResources.add(Pair(resourceFile, resourceGenerator.second))
    }
  }
  return generatedResources.takeIf { it.isNotEmpty() } ?: emptyList()
}
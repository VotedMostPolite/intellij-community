// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.projectModel.ProjectModelBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class ProjectFileType implements InternalFileType, FileType.CharsetHintSupplied {
  public static final ProjectFileType INSTANCE = new ProjectFileType();

  public static final String DEFAULT_EXTENSION = "ipr";
  public static final String DOT_DEFAULT_EXTENSION = ".ipr";

  private ProjectFileType() { }

  @Override
  @NotNull
  public String getName() {
    return "IDEA_PROJECT";
  }

  @Override
  @NotNull
  public String getDescription() {
    return ProjectModelBundle.message("filetype.description.idea.project");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.IdeaModule;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public @NotNull CharsetHint getCharsetHint() {
    return new CharsetHint.ForcedCharset(StandardCharsets.UTF_8);
  }
}
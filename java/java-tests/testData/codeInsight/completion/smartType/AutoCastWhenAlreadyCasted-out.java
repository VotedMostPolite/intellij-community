class B {
  {
      Object o;
      if (o instanceof String) {
          String s = (String) o;
          String ss = (String) o;<caret>
      }
  }
}
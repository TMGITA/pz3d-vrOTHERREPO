package com.pavelvoronin.pz3d;
public final class TreeRenderer {
    public static Object environment() { return TreeEnvironment.state; }
    public static void change() { TreeEnvironment.state=new TreeEnvironment.State(); }
}
final class TreeEnvironment {
    static State state=new State();
    static final class State {}
}

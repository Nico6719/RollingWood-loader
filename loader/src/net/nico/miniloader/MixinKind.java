package net.nico.miniloader;

/** 织入种类。对标 Fabric Mixin 的 @Inject 与 @Redirect。 */
public enum MixinKind {
    /** 往目标方法的某个位置插入一段调用。 */
    INJECT,
    /** 把目标方法里的某一次方法调用，整个换成我们的调用。 */
    REDIRECT
}

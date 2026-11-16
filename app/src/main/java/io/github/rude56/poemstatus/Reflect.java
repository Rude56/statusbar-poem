package io.github.rude56.poemstatus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 现代 libxposed API 的反射小工具。
 *
 * <p>旧 API 有 {@code XposedHelpers} 这一整套便捷方法，现代 API 有意不再提供
 * （框架只管 hook，查找与调用交给模块自己做）。本类只补上本项目真正用到的几个：
 * 按名字找类、按签名找方法、读写字段、调方法。
 *
 * <p><b>为什么值得单独一层</b>：这些调用全都可能因为 ROM 差异抛
 * {@code NoSuchMethodException} / {@code NoSuchFieldException}，
 * 而状态栏 Hook 最怕的就是「一个反射失败把整条初始化链打断」。
 * 所以统一在这里捕获并返回 null，由调用方决定降级策略。
 */
final class Reflect {

    private Reflect() {
    }

    /** 按名字找类（先在给定 ClassLoader 里找，失败再试系统 ClassLoader）。 */
    static Class<?> findClass(String name, ClassLoader cl) {
        if (name == null) {
            return null;
        }
        try {
            if (cl != null) {
                return Class.forName(name, false, cl);
            }
        } catch (Throwable ignored) {
            // 继续试下一个来源。
        }
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 按「方法名 + 参数类型名」找方法。
     *
     * <p><b>数组要特别当心</b>：{@code Class.getName()} 对数组返回的是
     * JVM 描述符形式 {@code [Lcom.android.internal.view.AppearanceRegion;}，
     * 而不是源码里写的 {@code com.android.internal.view.AppearanceRegion[]}。
     * 只按源码写法比较的话，所有带数组参数的方法都会静默匹配失败 ——
     * 这正是 {@code onStatusBarAppearanceChanged} 一开始挂不上的原因。
     * 所以这里两种写法都接受。
     *
     * @param paramNames 参数类型名；{@code null} 表示该位置不校验类型
     */
    static Method findMethod(Class<?> cls, String name, String... paramNames) {
        if (cls == null || name == null) {
            return null;
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (!name.equals(m.getName())) {
                continue;
            }
            if (paramNames == null) {
                if (m.getParameterCount() == 0) {
                    return m;
                }
                continue;
            }
            Class<?>[] ps = m.getParameterTypes();
            if (ps.length != paramNames.length) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < ps.length; i++) {
                if (paramNames[i] != null && !sameTypeName(ps[i], paramNames[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /** 类型名比较：同时接受源码写法（{@code a.b.C[]}）与 JVM 描述符写法（{@code [La.b.C;}）。 */
    private static boolean sameTypeName(Class<?> type, String wanted) {
        String actual = type.getName();
        if (actual.equals(wanted)) {
            return true;
        }
        // "a.b.C[]" → "[La.b.C;"
        if (wanted.endsWith("[]")) {
            String elem = wanted.substring(0, wanted.length() - 2);
            String descriptor = type.isArray() ? "[L" + elem + ";" : null;
            if (descriptor != null && descriptor.equals(actual)) {
                return true;
            }
            // 多维 / 基本类型数组退化为按元素名比较。
            return type.isArray() && type.getComponentType() != null
                    && type.getComponentType().getName().equals(elem);
        }
        return false;
    }

    /** 找无参方法。 */
    static Method findNoArgMethod(Class<?> cls, String name) {
        return findMethod(cls, name);
    }

    /**
     * 宽松匹配：按「方法名 + 参数个数（+ 首参是否数组）」找。
     *
     * <p>用于那些签名在 ROM 之间会微调的钩子点 —— 精确匹配失败时用它兜底，
     * 总比「整条特性静默失效」好。只在初始化时调用一次，不在热路径上。
     */
    static Method findMethodLoose(Class<?> cls, String name, int paramCount, boolean firstIsArray) {
        if (cls == null || name == null) {
            return null;
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (!name.equals(m.getName()) || m.getParameterCount() != paramCount) {
                continue;
            }
            if (firstIsArray && paramCount > 0 && !m.getParameterTypes()[0].isArray()) {
                continue;
            }
            m.setAccessible(true);
            return m;
        }
        return null;
    }

    /** 读实例字段（含父类）。 */
    static Object getField(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    /** 调无参方法（含父类）。 */
    static Object call(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        Class<?> c = target.getClass();
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (name.equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(target);
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }
}

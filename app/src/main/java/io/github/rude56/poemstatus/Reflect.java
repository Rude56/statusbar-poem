package io.github.rude56.poemstatus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 本地反射工具：现代 libxposed API 只负责 hook，类/方法查找与字段调用由模块自备（{@code XposedHelpers} 的对等物）。
 * 所有调用都可能因 ROM 差异失败，这里统一捕获并返回 null，由调用方决定降级策略。
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
     * 按「方法名 + 参数类型名」找方法。数组参数两种写法都接受：{@code Class.getName()} 返回 JVM 描述符
     * {@code [La.b.C;}，源码写法是 {@code a.b.C[]}，只比一种会静默匹配失败。
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

    /** 宽松匹配：按「方法名 + 参数个数（+ 首参是否数组）」找，用于签名在 ROM 之间会微调的钩子点；只在初始化时调用。 */
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

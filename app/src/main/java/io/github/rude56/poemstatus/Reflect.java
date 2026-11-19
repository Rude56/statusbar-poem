package io.github.rude56.poemstatus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Reflect {
    private Reflect() {
    }
    static Class<?> findClass(String name, ClassLoader cl) {
        if (name == null) {
            return null;
        }
        try {
            if (cl != null) {
                return Class.forName(name, false, cl);
            }
        } catch (Throwable ignored) {

        }
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

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

    private static boolean sameTypeName(Class<?> type, String wanted) {
        String actual = type.getName();
        if (actual.equals(wanted)) {
            return true;
        }

        if (wanted.endsWith("[]")) {
            String elem = wanted.substring(0, wanted.length() - 2);
            String descriptor = type.isArray() ? "[L" + elem + ";" : null;
            if (descriptor != null && descriptor.equals(actual)) {
                return true;
            }

            return type.isArray() && type.getComponentType() != null
                    && type.getComponentType().getName().equals(elem);
        }
        return false;
    }

    static Method findNoArgMethod(Class<?> cls, String name) {
        return findMethod(cls, name);
    }

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

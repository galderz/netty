/*
 * Copyright 2025 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Self-contained platform detection utilities shared by {@link PlatformDependent0} and
 * {@link VarHandleSupport}.
 * <p>
 * This class has no dependency on {@link PlatformDependent} or {@link PlatformDependent0} so
 * it is safe for build-time initialization in GraalVM native image while those classes are
 * runtime-initialized.
 */
final class PlatformCheck {

    /**
     * Cached Java major version, parsed once from {@code java.specification.version}.
     */
    static final int JAVA_VERSION = parseJavaVersion();

    /**
     * Returns {@code true} when running inside the GraalVM native image builder or inside
     * a native image at run time.  Checks for the {@code org.graalvm.nativeimage.imagecode}
     * system property which is set by the builder JVM and preserved at run time.
     */
    static boolean isNativeImage() {
        return SystemPropertyUtil.contains("org.graalvm.nativeimage.imagecode");
    }

    /**
     * Detects whether the platform supports unaligned memory access.
     * <p>
     * When {@code unsafe} is non-null the field is read via {@link Unsafe} (the fast path
     * used by {@link PlatformDependent0} at run time).  When {@code unsafe} is {@code null}
     * the field is read via reflection, which requires the appropriate module opens but
     * avoids any dependency on Unsafe (the path used by {@link VarHandleSupport} during
     * native image build).
     *
     * @param unsafe the Unsafe instance, or {@code null} to use reflection
     * @return {@code true} if the architecture supports unaligned access
     */
    static boolean detectUnaligned(Unsafe unsafe) {
        // 1. Honour the explicit system property override.
        String prop = SystemPropertyUtil.get("io.netty.unalignedAccess", "").trim();
        if ("true".equalsIgnoreCase(prop)) {
            return true;
        }
        if ("false".equalsIgnoreCase(prop)) {
            return false;
        }

        // 2. Try reading java.nio.Bits.UNALIGNED.
        int version = JAVA_VERSION;
        try {
            Class<?> bitsClass = Class.forName("java.nio.Bits", false,
                    ClassLoader.getSystemClassLoader());
            if (version >= 9) {
                String fieldName = version >= 11 ? "UNALIGNED" : "unaligned";
                try {
                    Field field = bitsClass.getDeclaredField(fieldName);
                    if (field.getType() == boolean.class) {
                        if (unsafe != null) {
                            // Fast path: use Unsafe to read without accessibility checks.
                            long offset = unsafe.staticFieldOffset(field);
                            Object base = unsafe.staticFieldBase(field);
                            return unsafe.getBoolean(base, offset);
                        } else {
                            // Reflection path: requires module opens for java.nio.
                            field.setAccessible(true);
                            return field.getBoolean(null);
                        }
                    }
                } catch (NoSuchFieldException ignore) {
                    // Field name changed or absent, try the method fallback below.
                }
            }
            // Method fallback (older JDKs or unexpected field layout).
            Method m = bitsClass.getDeclaredMethod("unaligned");
            Throwable cause = ReflectionUtil.trySetAccessible(m, true);
            if (cause == null) {
                return (Boolean) m.invoke(null);
            }
        } catch (Throwable ignore) {
            // Fall through to architecture heuristic.
        }

        // 3. Architecture heuristic.
        String arch = SystemPropertyUtil.get("os.arch", "");
        //noinspection DynamicRegexReplaceableByCompiledPattern
        return arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64)$");
    }

    // ---- Java version parsing (same logic as PlatformDependent0.javaVersion0) ----

    private static int parseJavaVersion() {
        return majorVersion(SystemPropertyUtil.get("java.specification.version", "1.6"));
    }

    // Package-private for testing.
    static int majorVersion(String javaSpecVersion) {
        final String[] components = javaSpecVersion.split("\\.");
        final int[] version = new int[components.length];
        for (int i = 0; i < components.length; i++) {
            version[i] = Integer.parseInt(components[i]);
        }
        if (version[0] == 1) {
            assert version[1] >= 6;
            return version[1];
        }
        return version[0];
    }

    private PlatformCheck() {
    }
}

/*
 * Copyright 2026 The Netty Project
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

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Determines VarHandle availability at class initialization time.
 * <p>
 * This class is designed to be safe for build-time initialization in GraalVM native image.
 * It does NOT depend on {@link PlatformDependent} or {@link PlatformDependent0} to avoid
 * triggering their class initialization, since those classes are runtime-initialized in
 * frameworks like Quarkus.
 */
public final class VarHandleSupport {

    static final boolean VAR_HANDLE;

    static boolean varHandleAvailable;
    static Throwable varHandleFailure;

    static {
        VAR_HANDLE = initializeVarHandle();
    }

    private static boolean initializeVarHandle() {
        // Determine Java version directly without going through PlatformDependent0,
        // to avoid triggering class initialization of runtime-initialized classes.
        int javaVersion = javaVersion0();
        if (javaVersion < 9) {
            return false;
        }

        // When running on a regular JVM (not a native image build), skip VarHandle
        // initialization if the platform supports unaligned access via Unsafe — in that
        // case VarHandle is unnecessary overhead. During a native image build we must
        // always initialize VarHandle because Unsafe may be unavailable at run time.
        if (!isNativeImageBuild() && isUnaligned0()) {
            return false;
        }

        try {
            VarHandle.storeStoreFence();
            varHandleAvailable = VarHandleFactory.isSupported();
            varHandleFailure = VarHandleFactory.unavailableCause();
        } catch (Throwable t) {
            // no-op
            varHandleFailure = t;
        }

        return varHandleAvailable &&
            SystemPropertyUtil.getBoolean("io.netty.varHandle.enabled", varHandleAvailable);
    }

    /**
     * Returns {@code true} if we are running inside the GraalVM native image builder.
     * Checks for {@code org.graalvm.nativeimage.imagecode} which is set by the builder JVM.
     */
    private static boolean isNativeImageBuild() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    /**
     * Determines whether the platform supports unaligned memory access, without depending
     * on {@link PlatformDependent0}. Reads {@code java.nio.Bits.UNALIGNED} reflectively,
     * falling back to an architecture name heuristic.
     */
    private static boolean isUnaligned0() {
        // Honour the explicit override first.
        String prop = System.getProperty("io.netty.unalignedAccess", "").trim();
        if ("true".equalsIgnoreCase(prop)) {
            return true;
        }
        if ("false".equalsIgnoreCase(prop)) {
            return false;
        }

        // Try reading java.nio.Bits.UNALIGNED reflectively (Java 11+).
        try {
            Class<?> bitsClass = Class.forName("java.nio.Bits", false, ClassLoader.getSystemClassLoader());
            Field field = bitsClass.getDeclaredField("UNALIGNED");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (Throwable ignore) {
            // fall through
        }

        // Try the method (older JDKs).
        try {
            Class<?> bitsClass = Class.forName("java.nio.Bits", false, ClassLoader.getSystemClassLoader());
            Method m = bitsClass.getDeclaredMethod("unaligned");
            m.setAccessible(true);
            return (Boolean) m.invoke(null);
        } catch (Throwable ignore) {
            // fall through
        }

        // Last resort: architecture heuristic.
        String arch = System.getProperty("os.arch", "");
        return arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64|aarch64)$");
    }

    private static int javaVersion0() {
        try {
            String specVersion = System.getProperty("java.specification.version");
            if (specVersion != null) {
                if (specVersion.startsWith("1.")) {
                    return Integer.parseInt(specVersion.substring(2));
                }
                final int dotIndex = specVersion.indexOf('.');
                if (dotIndex > 0) {
                    return Integer.parseInt(specVersion.substring(0, dotIndex));
                }
                return Integer.parseInt(specVersion);
            }
        } catch (Exception e) {
            // ignore
        }
        return 8;
    }

    private VarHandleSupport() {
        // only static method supported
    }
}

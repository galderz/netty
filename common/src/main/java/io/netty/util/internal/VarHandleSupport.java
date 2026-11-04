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

import java.lang.invoke.VarHandle;

/**
 * Holder class for the VarHandle availability flag and related VarHandle accessors.
 * <p>
 * This class is intentionally separated from {@link PlatformDependent} so that it can be
 * initialized at build time in GraalVM native image. When initialized at build time,
 * the {@link #VAR_HANDLE} constant and the VarHandle instances in {@link VarHandleFactory}
 * become true constants that the AOT compiler can fold, eliminating the performance regression
 * that occurs when VarHandle fields are only known at runtime.
 * </p>
 *
 * @see <a href="https://github.com/oracle/graal/issues/14476">graal#14476</a>
 */
final class VarHandleSupport {

    static final boolean VAR_HANDLE;

    static {
        VAR_HANDLE = initializeVarHandle();
    }

    private VarHandleSupport() {
    }

    private static boolean initializeVarHandle() {
        // Check Java version without triggering PlatformDependent0's full initialization,
        // which would pull in Unsafe and prevent build-time initialization in native image.
        if (javaVersion() < 9) {
            return false;
        }
        boolean varHandleAvailable = false;
        Throwable varHandleFailure;
        try {
            VarHandle.storeStoreFence();
            varHandleAvailable = VarHandleFactory.isSupported();
            varHandleFailure = VarHandleFactory.unavailableCause();
        } catch (Throwable t) {
            varHandleFailure = t;
        }
        boolean varHandleEnabled = varHandleAvailable &&
                SystemPropertyUtil.getBoolean("io.netty.varHandle.enabled", varHandleAvailable);
        return varHandleEnabled;
    }

    private static int javaVersion() {
        final String specVersion = SystemPropertyUtil.get("java.specification.version", "1.6");
        final String[] components = specVersion.split("\\.");
        final int[] version = new int[components.length];
        for (int i = 0; i < components.length; i++) {
            version[i] = Integer.parseInt(components[i]);
        }
        if (version[0] == 1) {
            return version.length > 1 ? version[1] : version[0];
        }
        return version[0];
    }
}

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
 * Determines VarHandle availability at class initialization time.
 * <p>
 * This class is designed to be safe for build-time initialization in GraalVM native image.
 * It depends only on {@link PlatformCheck} (also build-time safe) and never touches
 * {@link PlatformDependent} or {@link PlatformDependent0}.
 * <p>
 * On a regular JVM where Unsafe unaligned access is available, VarHandle initialisation is
 * skipped as an optimisation (Unsafe is faster).  During a native image build, VarHandle is
 * always initialised because Unsafe may be unavailable at run time.
 *
 * @see <a href="https://github.com/oracle/graal/issues/14476">graal#14476</a>
 */
public final class VarHandleSupport {

    static final boolean VAR_HANDLE;

    static boolean varHandleAvailable;
    static Throwable varHandleFailure;

    static {
        VAR_HANDLE = initializeVarHandle();
    }

    private static boolean initializeVarHandle() {
        if (PlatformCheck.JAVA_VERSION < 9) {
            return false;
        }

        // On a regular JVM where the architecture supports unaligned access, Unsafe will
        // handle multi-byte operations so VarHandle is unnecessary overhead.  Skip
        // initialisation to avoid loading VarHandleFactory eagerly.
        //
        // During a native image build we must always initialise VarHandle because Unsafe
        // may be disabled at run time (io.netty.noUnsafe=true).  The unaligned check uses
        // reflection (null Unsafe) so it does not trigger PlatformDependent0.
        if (!PlatformCheck.isNativeImage() && PlatformCheck.detectUnaligned(null)) {
            return false;
        }

        try {
            VarHandle.storeStoreFence();
            varHandleAvailable = VarHandleFactory.isSupported();
            varHandleFailure = VarHandleFactory.unavailableCause();
        } catch (Throwable t) {
            varHandleFailure = t;
        }

        return varHandleAvailable &&
            SystemPropertyUtil.getBoolean("io.netty.varHandle.enabled", varHandleAvailable);
    }

    private VarHandleSupport() {
    }
}

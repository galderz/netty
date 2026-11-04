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
package io.netty.microbench.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class VarHandleBenchmark extends AbstractMicrobenchmark {

    @Param({"256"})
    private int size;

    private ByteBuf[] bufs;
    private int index;
    private volatile int result;

    @Setup
    public void setup() {
        System.out.printf("hasVarHandle: %b%n", PlatformDependent.hasVarHandle());
        System.out.printf("hasUnsafe: %b%n", PlatformDependent.hasUnsafe());
        System.out.printf("canUnalignedAccess: %b%n", PlatformDependent.canUnalignedAccess());

        Random rnd = new Random(123);

        bufs = new ByteBuf[16];
        for (int i = 0; i < 16; i++) {
            ByteBuf buf;
            switch (i % 4) {
                case 0: buf = Unpooled.buffer(size); break;
                case 1: buf = Unpooled.directBuffer(size); break;
                case 2: buf = PooledByteBufAllocator.DEFAULT.heapBuffer(size); break;
                default: buf = PooledByteBufAllocator.DEFAULT.directBuffer(size); break;
            }
            int zPos = size / 2 + rnd.nextInt(size / 2);
            for (int j = 0; j < size; j++) {
                buf.writeByte(j == zPos ? 'Z' : (byte) (rnd.nextInt(89) + 1));
            }
            bufs[i] = buf;
        }
        index = 0;
    }

    @TearDown
    public void tearDown() {
        for (ByteBuf buf : bufs) {
            buf.release();
        }
    }

    @Benchmark
    public void indexOf() {
        result = ByteBufUtil.indexOf(bufs[index], 0, bufs[index].writerIndex(), (byte) 'Z');
    }
}

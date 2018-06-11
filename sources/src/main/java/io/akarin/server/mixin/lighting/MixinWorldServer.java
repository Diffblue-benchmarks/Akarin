/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.akarin.server.mixin.lighting;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.akarin.api.Akari;
import io.akarin.api.mixin.IMixinChunk;
import io.akarin.api.mixin.IMixinWorldServer;
import io.akarin.server.core.AkarinGlobalConfig;
import net.minecraft.server.BlockPosition;
import net.minecraft.server.Chunk;
import net.minecraft.server.EnumDirection;
import net.minecraft.server.EnumSkyBlock;
import net.minecraft.server.IBlockData;
import net.minecraft.server.MCUtil;
import net.minecraft.server.MathHelper;
import net.minecraft.server.WorldServer;
import net.minecraft.server.BlockPosition.PooledBlockPosition;

@Mixin(value = WorldServer.class, remap = false)
public abstract class MixinWorldServer extends MixinWorld implements IMixinWorldServer {
    
    private static final int NUM_XZ_BITS = 4;
    private static final int NUM_SHORT_Y_BITS = 8;
    private static final short XZ_MASK = 0xF;
    private static final short Y_SHORT_MASK = 0xFF;
    
    private final ExecutorService lightExecutorService = preparExecutorService();;
    
    private ExecutorService preparExecutorService() {
        return AkarinGlobalConfig.asyncLightingWorkStealing ?
                Executors.newFixedThreadPool(AkarinGlobalConfig.asyncLightingThreads, new ThreadFactoryBuilder().setNameFormat("Akarin Async Light Thread").build()) : Executors.newWorkStealingPool(AkarinGlobalConfig.asyncLightingThreads);
    }
    
    @Override
    public boolean checkLightFor(EnumSkyBlock lightType, BlockPosition pos) { // PAIL: checkLightFor
        return updateLightAsync(lightType, pos, null);
    }
    
    public boolean checkLightAsync(EnumSkyBlock lightType, BlockPosition pos, Chunk currentChunk, List<Chunk> neighbors) {
        // Sponge - This check is not needed as neighbors are checked in updateLightAsync
        if (false && !this.areChunksLoaded(pos, 17, false)) {
            return false;
        } else {
            final IMixinChunk spongeChunk = (IMixinChunk) currentChunk;
            int i = 0;
            int j = 0;
            int k = this.getLightForAsync(lightType, pos, currentChunk, neighbors); // Sponge - use thread safe method
            int l = this.getRawBlockLightAsync(lightType, pos, currentChunk, neighbors); // Sponge - use thread safe method
            int i1 = pos.getX();
            int j1 = pos.getY();
            int k1 = pos.getZ();

            if (l > k) {
                this.J[j++] = 133152; // PAIL: lightUpdateBlockList
            } else if (l < k) {
                this.J[j++] = 133152 | k << 18; // PAIL: lightUpdateBlockList

                while (i < j) {
                    int l1 = this.J[i++]; // PAIL: lightUpdateBlockList
                    int i2 = (l1 & 63) - 32 + i1;
                    int j2 = (l1 >> 6 & 63) - 32 + j1;
                    int k2 = (l1 >> 12 & 63) - 32 + k1;
                    int l2 = l1 >> 18 & 15;
                    BlockPosition blockpos = new BlockPosition(i2, j2, k2);
                    int i3 = this.getLightForAsync(lightType, blockpos, currentChunk, neighbors); // Sponge - use thread safe method

                    if (i3 == l2) {
                        this.setLightForAsync(lightType, blockpos, 0, currentChunk, neighbors); // Sponge - use thread safe method

                        if (l2 > 0) {
                            int j3 = MathHelper.a(i2 - i1); // TODO MathHelper
                            int k3 = Math.abs(j2 - j1);
                            int l3 = Math.abs(k2 - k1);

                            if (j3 + k3 + l3 < 17) {
                                PooledBlockPosition blockpos$pooledmutableblockpos = PooledBlockPosition.aquire();

                                for (EnumDirection enumfacing : EnumDirection.values()) {
                                    int i4 = i2 + enumfacing.getAdjacentX();
                                    int j4 = j2 + enumfacing.getAdjacentX();
                                    int k4 = k2 + enumfacing.getAdjacentX();
                                    blockpos$pooledmutableblockpos.setValues(i4, j4, k4);
                                    // Sponge start - get chunk safely
                                    final Chunk pooledChunk = this.getLightChunk(blockpos$pooledmutableblockpos, currentChunk, neighbors);
                                    if (pooledChunk == null) {
                                        continue;
                                    }
                                    int l4 = Math.max(1, pooledChunk.getBlockData(blockpos$pooledmutableblockpos).c()); // PAIL: getLightOpacity
                                    i3 = this.getLightForAsync(lightType, blockpos$pooledmutableblockpos, currentChunk, neighbors);
                                    // Sponge end

                                    if (i3 == l2 - l4 && j < this.J.length) { // PAIL: lightUpdateBlockList
                                        this.J[j++] = i4 - i1 + 32 | j4 - j1 + 32 << 6 | k4 - k1 + 32 << 12 | l2 - l4 << 18; // PAIL: lightUpdateBlockList
                                    }
                                }
                                
                                blockpos$pooledmutableblockpos.free();
                            }
                        }
                    }
                }

                i = 0;
            }

            while (i < j) {
                int i5 = this.J[i++]; // PAIL: lightUpdateBlockList
                int j5 = (i5 & 63) - 32 + i1;
                int k5 = (i5 >> 6 & 63) - 32 + j1;
                int l5 = (i5 >> 12 & 63) - 32 + k1;
                BlockPosition blockpos1 = new BlockPosition(j5, k5, l5);
                int i6 = this.getLightForAsync(lightType, blockpos1, currentChunk, neighbors); // Sponge - use thread safe method
                int j6 = this.getRawBlockLightAsync(lightType, blockpos1, currentChunk, neighbors); // Sponge - use thread safe method

                if (j6 != i6) {
                    this.setLightForAsync(lightType, blockpos1, j6, currentChunk, neighbors); // Sponge - use thread safe method

                    if (j6 > i6) {
                        int k6 = Math.abs(j5 - i1);
                        int l6 = Math.abs(k5 - j1);
                        int i7 = Math.abs(l5 - k1);
                        boolean flag = j < this.J.length - 6; // PAIL: lightUpdateBlockList

                        if (k6 + l6 + i7 < 17 && flag) {
                            // Sponge start - use thread safe method getLightForAsync
                            if (this.getLightForAsync(lightType, blockpos1.west(), currentChunk, neighbors) < j6) {
                                this.J[j++] = j5 - 1 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 - k1 + 32 << 12); // PAIL: lightUpdateBlockList
                            }

                            if (this.getLightForAsync(lightType, blockpos1.east(), currentChunk, neighbors) < j6) {
                                this.J[j++] = j5 + 1 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 - k1 + 32 << 12); // PAIL: lightUpdateBlockList
                            }

                            if (this.getLightForAsync(lightType, blockpos1.down(), currentChunk, neighbors) < j6) {
                                this.J[j++] = j5 - i1 + 32 + (k5 - 1 - j1 + 32 << 6) + (l5 - k1 + 32 << 12); // PAIL: lightUpdateBlockList
                            }

                            if (this.getLightForAsync(lightType, blockpos1.up(), currentChunk, neighbors) < j6) {
                                this.J[j++] = j5 - i1 + 32 + (k5 + 1 - j1 + 32 << 6) + (l5 - k1 + 32 << 12); // PAIL: lightUpdateBlockList
                            }

                            if (this.getLightForAsync(lightType, blockpos1.north(), currentChunk, neighbors) < j6) {
                                this.J[j++] = j5 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 - 1 - k1 + 32 << 12); // PAIL: lightUpdateBlockList
                            }

                            if (this.getLightForAsync(lightType, blockpos1.south(), currentChunk, neighbors) < j6) {
                                this.J[j++] = j5 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 + 1 - k1 + 32 << 12); // PAIL: lightUpdateBlockList
                            }
                            // Sponge end
                        }
                    }
                }
            }

            // Sponge start - Asynchronous light updates
            spongeChunk.getQueuedLightingUpdates(lightType).remove((Short) this.blockPosToShort(pos));
            spongeChunk.getPendingLightUpdates().decrementAndGet();
            for (Chunk neighborChunk : neighbors) {
                final IMixinChunk neighbor = (IMixinChunk) neighborChunk;
                neighbor.getPendingLightUpdates().decrementAndGet();
            }
            // Sponge end
            return true;
        }
    }
    
    @Override
    public boolean updateLightAsync(EnumSkyBlock lightType, BlockPosition pos, @Nullable Chunk currentChunk) {
        if (this.getMinecraftServer().isStopped() || this.lightExecutorService.isShutdown()) {
            return false;
        }
        
        if (currentChunk == null) {
            currentChunk = MCUtil.getLoadedChunkWithoutMarkingActive(chunkProvider, pos.getX() >> 4, pos.getZ() >> 4);
        }

        final IMixinChunk spongeChunk = (IMixinChunk) currentChunk;
        if (currentChunk == null || currentChunk.isUnloading() || !spongeChunk.areNeighborsLoaded()) {
            return false;
        }

        final short shortPos = this.blockPosToShort(pos);
        if (spongeChunk.getQueuedLightingUpdates(lightType).contains(shortPos)) {
            return false;
        }

        final Chunk chunk = currentChunk;
        spongeChunk.getQueuedLightingUpdates(lightType).add(shortPos);
        spongeChunk.getPendingLightUpdates().incrementAndGet();
        spongeChunk.setLightUpdateTime(chunk.getWorld().getTime());

        List<Chunk> neighbors = spongeChunk.getNeighbors();
        // add diagonal chunks
        Chunk southEastChunk = ((IMixinChunk) spongeChunk.getNeighborChunk(0)).getNeighborChunk(2);
        Chunk southWestChunk = ((IMixinChunk) spongeChunk.getNeighborChunk(0)).getNeighborChunk(3);
        Chunk northEastChunk = ((IMixinChunk) spongeChunk.getNeighborChunk(1)).getNeighborChunk(2);
        Chunk northWestChunk = ((IMixinChunk) spongeChunk.getNeighborChunk(1)).getNeighborChunk(3);
        if (southEastChunk != null) {
            neighbors.add(southEastChunk);
        }
        if (southWestChunk != null) {
            neighbors.add(southWestChunk);
        }
        if (northEastChunk != null) {
            neighbors.add(northEastChunk);
        }
        if (northWestChunk != null) {
            neighbors.add(northWestChunk);
        }

        for (Chunk neighborChunk : neighbors) {
            final IMixinChunk neighbor = (IMixinChunk) neighborChunk;
            neighbor.getPendingLightUpdates().incrementAndGet();
            neighbor.setLightUpdateTime(chunk.getWorld().getTime());
        }

        if (Akari.isPrimaryThread()) { // Akarin
            this.lightExecutorService.execute(() -> {
                this.checkLightAsync(lightType, pos, chunk, neighbors);
            });
        } else {
            this.checkLightAsync(lightType, pos, chunk, neighbors);
        }
        
        return true;
    }
    
    @Override
    public ExecutorService getLightingExecutor() {
        return this.lightExecutorService;
    }
    
    // Thread safe methods to retrieve a chunk during async light updates
    // Each method avoids calling getLoadedChunk and instead accesses the passed neighbor chunk list to avoid concurrency issues
    public Chunk getLightChunk(BlockPosition pos, Chunk currentChunk, List<Chunk> neighbors) {
        if (currentChunk.a(pos.getX() >> 4, pos.getZ() >> 4)) { // PAIL: isAtLocation
            if (currentChunk.isUnloading()) {
                return null;
            }
            return currentChunk;
        }
        for (Chunk neighbor : neighbors) {
            if (neighbor.a(pos.getX() >> 4, pos.getZ() >> 4)) { // PAIL: isAtLocation
                if (neighbor.isUnloading()) {
                    return null;
                }
                return neighbor;
            }
        }
        
        return null;
    }

    private int getLightForAsync(EnumSkyBlock lightType, BlockPosition pos, Chunk currentChunk, List<Chunk> neighbors) {
        if (pos.getY() < 0) {
            pos = new BlockPosition(pos.getX(), 0, pos.getZ());
        }
        if (!(pos.isValidLocation())) {
            return lightType.c; // PAIL: defaultLightValue
        }

        final Chunk chunk = this.getLightChunk(pos, currentChunk, neighbors);
        if (chunk == null || chunk.isUnloading()) {
            return lightType.c; // PAIL: defaultLightValue
        }

        return chunk.getBrightness(lightType, pos);
    }

    private int getRawBlockLightAsync(EnumSkyBlock lightType, BlockPosition pos, Chunk currentChunk, List<Chunk> neighbors) {
        final Chunk chunk = getLightChunk(pos, currentChunk, neighbors);
        if (chunk == null || chunk.isUnloading()) {
            return lightType.c; // PAIL: defaultLightValue
        }
        if (lightType == EnumSkyBlock.SKY && chunk.c(pos)) { // PAIL: canSeeSky
            return 15;
        } else {
            IBlockData blockState = chunk.getBlockData(pos);
            int blockLight = blockState.d(); // getLightValue
            int i = lightType == EnumSkyBlock.SKY ? 0 : blockLight;
            int j = blockState.c(); // PAIL: getLightOpacity

            if (j >= 15 && blockLight > 0) {
                j = 1;
            }

            if (j < 1) {
                j = 1;
            }

            if (j >= 15) {
                return 0;
            } else if (i >= 14) {
                return i;
            } else {
                for (EnumDirection facing : EnumDirection.values()) {
                    BlockPosition blockpos = pos.shift(facing);
                    int k = this.getLightForAsync(lightType, blockpos, currentChunk, neighbors) - j;
                    
                    if (k > i) {
                        i = k;
                    }
                    
                    if (i >= 14) {
                        return i;
                    }
                }

                return i;
            }
        }
    }

    public void setLightForAsync(EnumSkyBlock type, BlockPosition pos, int lightValue, Chunk currentChunk, List<Chunk> neighbors) {
        if (pos.isValidLocation()) {
            final Chunk chunk = this.getLightChunk(pos, currentChunk, neighbors);
            if (chunk != null && !chunk.isUnloading()) {
                chunk.a(type, pos, lightValue); // PAIL: setBrightness
                this.notifyLightSet(pos);
            }
        }
    }

    private short blockPosToShort(BlockPosition pos) {
        short serialized = (short) setNibble(0, pos.getX() & XZ_MASK, 0, NUM_XZ_BITS);
        serialized = (short) setNibble(serialized, pos.getY() & Y_SHORT_MASK, 1, NUM_SHORT_Y_BITS);
        serialized = (short) setNibble(serialized, pos.getZ() & XZ_MASK, 3, NUM_XZ_BITS);
        return serialized;
    }

    /**
     * Modifies bits in an integer.
     *
     * @param num Integer to modify
     * @param data Bits of data to add
     * @param which Index of nibble to start at
     * @param bitsToReplace The number of bits to replace starting from nibble index
     * @return The modified integer
     */
    private int setNibble(int num, int data, int which, int bitsToReplace) {
        return (num & ~(bitsToReplace << (which * 4)) | (data << (which * 4)));
    }
}

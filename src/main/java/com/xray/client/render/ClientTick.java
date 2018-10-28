package com.xray.client.render;

import com.xray.client.xray.XRayController;
import com.xray.common.XRay;
import com.xray.common.reference.BlockId;
import com.xray.common.reference.block.BlockData;
import com.xray.common.reference.block.BlockInfo;
import com.xray.common.utils.WorldRegion;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientTick implements Runnable
{
	private final WorldRegion box;

	public ClientTick( WorldRegion region )
	{
		box = region;
	}

	@Override
	public void run() // Our thread code for finding ores near the player.
	{
		blockFinder();
	}

	/**
	 * Use XRayController.requestBlockFinder() to trigger a scan.
	 */
	private void blockFinder() {
		Map<BlockId, int[]> ores = XRayController.searchList.getDrawableBlocks();
		if ( ores.isEmpty() )
			return; // no need to scan the region if there's nothing to find

		final World world = XRay.mc.world;
		final List<BlockInfo> temp = new ArrayList<>();
		BlockId key; // Search key for the map
		int lowBoundX, highBoundX, lowBoundY, highBoundY, lowBoundZ, highBoundZ;
		IBlockState currentState;
        String currentName;

		// Loop on chunks (x, z)
		for ( int chunkX = box.minChunkX; chunkX <= box.maxChunkX; chunkX++ )
		{
			// Pre-compute the extend bounds on X
			int x = chunkX << 4; // lowest x coord of the chunk in block/world coordinates
			lowBoundX = (x < box.minX) ? box.minX - x : 0; // lower bound for x within the extend
			highBoundX = (x + 15 > box.maxX) ? box.maxX - x : 15;// and higher bound. Basically, we clamp it to fit the radius.

			for ( int chunkZ = box.minChunkZ; chunkZ <= box.maxChunkZ; chunkZ++ )
			{
				// Time to get the chunk (16x256x16) and split it into 16 vertical extends (16x16x16)
				Chunk chunk = world.getChunkFromChunkCoords( chunkX, chunkZ );
				if ( chunk == null || !chunk.isLoaded() ) {
					continue; // We won't find anything interesting in unloaded chunks
				}
				ExtendedBlockStorage[] extendsList = chunk.getBlockStorageArray();

				// Pre-compute the extend bounds on Z
				int z = chunkZ << 4;
				lowBoundZ = (z < box.minZ) ? box.minZ - z : 0;
				highBoundZ = (z + 15 > box.maxZ) ? box.maxZ - z : 15;

				// Loop on the extends around the player's layer (6 down, 2 up)
				for ( int curExtend = box.minChunkY; curExtend <= box.maxChunkY; curExtend++ )
				{
					ExtendedBlockStorage ebs = extendsList[curExtend];
					if (ebs == null) // happens quite often!
						continue;

					// Pre-compute the extend bounds on Y
					int y = curExtend << 4;
					lowBoundY = (y < box.minY) ? box.minY - y : 0;
					highBoundY = (y + 15 > box.maxY) ? box.maxY - y : 15;

					// Now that we have an extend, let's check all its blocks
					for ( int i = lowBoundX; i <= highBoundX; i++ ) {
						for ( int j = lowBoundY; j <= highBoundY; j++ ) {
							for ( int k = lowBoundZ; k <= highBoundZ; k++ ) {
								currentState = ebs.get(i, j, k);

								// Reject blacklisted blocks
								if( XRayController.blackList.contains(currentState.getBlock()) )
									continue;

                                currentName = currentState.getBlock().getLocalizedName();
								if (XRayController.blockStore.store.containsKey(currentName)) // The reason for using Set/Map
								{
								    // Looking at default allows us to skip the for loop below
								    if( XRayController.blockStore.defaultContains(currentName) ) {
                                      
								        BlockData tmp = XRayController.blockStore.store.get(currentName).getFirst();
								        if( tmp == null ) // fail safe
								            continue;

                                        temp.add(new BlockInfo(x + i, y + j, z + k, tmp.getOutline().getColor())); // Add this block to the temp list using world coordinates

                                    } else {
                                        for (BlockData data : XRayController.blockStore.store.get(currentState.getBlock().getLocalizedName())) {
                                            if (Block.getStateId(data.state) == Block.getStateId(currentState))
                                                temp.add(new BlockInfo(x + i, y + j, z + k, data.getOutline().getColor())); // Add this block to the temp list using world coordinates
                                        }
                                    }
								}
							}
						}
					}
				}
			}
		}
		final BlockPos playerPos = XRay.mc.player.getPosition();
		temp.sort((t, t1) -> Double.compare(t1.distanceSq(playerPos), t.distanceSq(playerPos)));
		XrayRenderer.ores.clear();
		XrayRenderer.ores.addAll( temp ); // Add all our found blocks to the XrayRenderer.ores list. To be use by XrayRenderer when drawing.
	}

	/**
	 * Single-block version of blockFinder. Can safely be called directly
	 * for quick block check.
	 * @param pos the BlockPos to check
	 * @param state the current state of the block
	 * @param add true if the block was added to world, false if it was removed
	 */
	public static void checkBlock( BlockPos pos, IBlockState state, boolean add )
	{
		if ( !XRayController.drawOres() ) return; // just pass

		// Let's see if the block to check is an ore we monitor
		int[] color = XRayController.searchList.getDrawableBlocks().get( BlockId.fromBlockState(state) );
		if ( color != null ) // it's a block we are monitoring
		{
			if ( add )	// the block was added to the world, let's add it to the drawing buffer
				XrayRenderer.ores.add( new BlockInfo(pos, color) );
			else		// it was removed from the world, let's remove it from the buffer as well
				XrayRenderer.ores.remove( new BlockInfo(pos, null) );
		}
	}
}

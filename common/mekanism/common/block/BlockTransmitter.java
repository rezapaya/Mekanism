package mekanism.common.block;

import java.util.Arrays;
import java.util.List;

import mekanism.api.gas.GasTransmission;
import mekanism.api.gas.ITubeConnection;
import mekanism.api.transmitters.ITransmitter;
import mekanism.client.ClientProxy;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.PacketHandler.Transmission;
import mekanism.common.network.PacketTransmitterUpdate;
import mekanism.common.network.PacketTransmitterUpdate.PacketType;
import mekanism.common.tileentity.TileEntityDiversionTransporter;
import mekanism.common.tileentity.TileEntityLogisticalTransporter;
import mekanism.common.tileentity.TileEntityMechanicalPipe;
import mekanism.common.tileentity.TileEntityPressurizedTube;
import mekanism.common.tileentity.TileEntityUniversalCable;
import mekanism.common.transporter.TransporterStack;
import mekanism.common.util.CableUtils;
import mekanism.common.util.PipeUtils;
import mekanism.common.util.TransporterUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import buildcraft.api.tools.IToolWrench;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Block class for handling multiple transmitter IDs.
 * 0: Pressurized Tube
 * 1: Universal Cable
 * 2: Mechanical Pipe
 * 3: Logistical Transporter
 * 4: Restrictive Transporter
 * 5: Diversion Transporter
 * @author AidanBrady
 *
 */
public class BlockTransmitter extends Block
{
	public static final float SMALL_MIN_BOUND = 0.3125F;
	public static final float SMALL_MAX_BOUND = 0.6875F;
	
	public static final float LARGE_MIN_BOUND = 0.25F;
	public static final float LARGE_MAX_BOUND = 0.75F;
	
	public BlockTransmitter(int id)
	{
		super(id, Material.wood);
		setHardness(2.0F);
		setResistance(5.0F);
		setCreativeTab(Mekanism.tabMekanism);
	}
	
	public float getMinBound(IBlockAccess world, int x, int y, int z)
	{
		int metadata = world.getBlockMetadata(x, y, z);
		
		if(metadata < 2)
		{
			return SMALL_MIN_BOUND;
		}
		else {
			return LARGE_MIN_BOUND;
		}
	}
	
	public float getMaxBound(IBlockAccess world, int x, int y, int z)
	{
		int metadata = world.getBlockMetadata(x, y, z);
		
		if(metadata < 2)
		{
			return SMALL_MAX_BOUND;
		}
		else {
			return LARGE_MAX_BOUND;
		}
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister register) {}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void getSubBlocks(int i, CreativeTabs creativetabs, List list)
	{
		list.add(new ItemStack(i, 1, 0));
		list.add(new ItemStack(i, 1, 1));
		list.add(new ItemStack(i, 1, 2));
		list.add(new ItemStack(i, 1, 3));
		list.add(new ItemStack(i, 1, 4));
		list.add(new ItemStack(i, 1, 5));
	}
	
	@Override
	public int damageDropped(int i)
	{
		return i;
	}
	
	@Override
	public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB axisalignedbb, List list, Entity entity) 
	{
		boolean[] connectable = getConnectable(world, x, y, z);
		
		setBlockBounds(getMinBound(world, x, y, z), getMinBound(world, x, y, z), getMinBound(world, x, y, z), getMaxBound(world, x, y, z), getMaxBound(world, x, y, z), getMaxBound(world, x, y, z));
		super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, list, entity);

		if(connectable[4]) 
		{
			setBlockBounds(0.0F, getMinBound(world, x, y, z), getMinBound(world, x, y, z), getMaxBound(world, x, y, z), getMaxBound(world, x, y, z), getMaxBound(world, x, y, z));
			super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, list, entity);
		}

		if(connectable[5]) 
		{
			setBlockBounds(getMinBound(world, x, y, z), getMinBound(world, x, y, z), getMinBound(world, x, y, z), 1.0F, getMaxBound(world, x, y, z), getMaxBound(world, x, y, z));
			super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, list, entity);
		}

		if(connectable[0]) 
		{
			setBlockBounds(getMinBound(world, x, y, z), 0.0F, getMinBound(world, x, y, z), getMaxBound(world, x, y, z), getMaxBound(world, x, y, z), getMaxBound(world, x, y, z));
			super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, list, entity);
		}

		if(connectable[1])
		{
			setBlockBounds(getMinBound(world, x, y, z), getMinBound(world, x, y, z), getMinBound(world, x, y, z), getMaxBound(world, x, y, z), 1.0F, getMaxBound(world, x, y, z));
			super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, list, entity);
		}

		if(connectable[2])
		{
			setBlockBounds(getMinBound(world, x, y, z), getMinBound(world, x, y, z), 0.0F, getMaxBound(world, x, y, z), getMaxBound(world, x, y, z), getMaxBound(world, x, y, z));
			super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, list, entity);
		}

		if(connectable[3])
		{
			setBlockBounds(getMinBound(world, x, y, z), getMinBound(world, x, y, z), getMinBound(world, x, y, z), getMaxBound(world, x, y, z), getMaxBound(world, x, y, z), 1.0F);
			super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, list, entity);
		}

		setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
	}

	@Override
	public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) 
	{
		boolean[] connectable = getConnectable(world, x, y, z);
		
		if(connectable != null)
		{
			float minX = getMinBound(world, x, y, z);
			float minY = getMinBound(world, x, y, z);
			float minZ = getMinBound(world, x, y, z);
			float maxX = getMaxBound(world, x, y, z);
			float maxY = getMaxBound(world, x, y, z);
			float maxZ = getMaxBound(world, x, y, z);
	
			if(connectable[0])
			{
				minY = 0.0F;
			}
			
			if(connectable[1])
			{
				maxY = 1.0F;
			}
			
			if(connectable[2])
			{
				minZ = 0.0F;
			}
			
			if(connectable[3])
			{
				maxZ = 1.0F;
			}
			
			if(connectable[4])
			{
				minX = 0.0F;
			}
			
			if(connectable[5])
			{
				maxX = 1.0F;
			}
	
			return AxisAlignedBB.getBoundingBox(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
		}
		
		return super.getSelectedBoundingBoxFromPool(world, x, y, z);
	}
	
	public boolean[] getConnectable(IBlockAccess world, int x, int y, int z)
	{
		TileEntity tileEntity = world.getBlockTileEntity(x, y, z);
		
		boolean[] connectable = null;
		
		if(tileEntity != null)
		{	
			connectable = new boolean[] {false, false, false, false, false, false};
			
			if(world.getBlockMetadata(x, y, z) == 0)
			{
				ITubeConnection[] connections = GasTransmission.getConnections(tileEntity);
				
				for(ITubeConnection connection : connections)
				{
					if(connection != null)
					{
						int side = Arrays.asList(connections).indexOf(connection);
						
						if(connection.canTubeConnect(ForgeDirection.getOrientation(side).getOpposite()))
						{
							connectable[side] = true;
						}
					}
				}
			}
			else if(world.getBlockMetadata(x, y, z) == 1)
			{
				connectable = CableUtils.getConnections(tileEntity);
			}
			else if(world.getBlockMetadata(x, y, z) == 2)
			{
				connectable = PipeUtils.getConnections(tileEntity);
			}
			else if(world.getBlockMetadata(x, y, z) == 3 || world.getBlockMetadata(x, y, z) == 4 || world.getBlockMetadata(x, y, z) == 5)
			{
				connectable = TransporterUtils.getConnections((TileEntityLogisticalTransporter)tileEntity);
			}
		}
		
		return connectable;
	}
	
	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z)
	{
		TileEntity tileEntity = world.getBlockTileEntity(x, y, z);
		
		float minX = getMinBound(world, x, y, z);
		float minY = getMinBound(world, x, y, z);
		float minZ = getMinBound(world, x, y, z);
		float maxX = getMaxBound(world, x, y, z);
		float maxY = getMaxBound(world, x, y, z);
		float maxZ = getMaxBound(world, x, y, z);
		
		boolean[] connectable = getConnectable(world, x, y, z);
			
		if(connectable != null)
		{
			if(connectable[0])
			{
				minY = 0.0F;
			}
			
			if(connectable[1])
			{
				maxY = 1.0F;
			}
			
			if(connectable[2])
			{
				minZ = 0.0F;
			}
			
			if(connectable[3])
			{
				maxZ = 1.0F;
			}
			
			if(connectable[4])
			{
				minX = 0.0F;
			}
			
			if(connectable[5])
			{
				maxX = 1.0F;
			}
			
			setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
		}
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, int blockID)
	{
		TileEntity tileEntity = world.getBlockTileEntity(x, y, z);

		if(!world.isRemote)
		{
			if(tileEntity instanceof ITransmitter)
			{
				((ITransmitter)tileEntity).refreshTransmitterNetwork();
				PacketHandler.sendPacket(Transmission.CLIENTS_DIM, new PacketTransmitterUpdate().setParams(PacketType.UPDATE, tileEntity), world.provider.dimensionId);
			}
		}
	}
	
	@Override
	public void onBlockAdded(World world, int x, int y, int z)
	{
		TileEntity tileEntity = world.getBlockTileEntity(x, y, z);

		if(!world.isRemote)
		{
			if(tileEntity instanceof ITransmitter)
			{
				((ITransmitter)tileEntity).refreshTransmitterNetwork();
				PacketHandler.sendPacket(Transmission.CLIENTS_DIM, new PacketTransmitterUpdate().setParams(PacketType.UPDATE, tileEntity), world.provider.dimensionId);
				
				if(tileEntity instanceof TileEntityUniversalCable)
				{
					((TileEntityUniversalCable)tileEntity).register();
				}
			}
		}
	}
	
	@Override
	public boolean isOpaqueCube()
	{
		return false;
	}
	
	@Override
	public boolean renderAsNormalBlock()
	{
		return false;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public int getRenderType()
	{
		return ClientProxy.TRANSMITTER_RENDER_ID;
	}
	
	@Override
	public boolean hasTileEntity(int metadata)
	{
		return true;
	}
	
	@Override
	public TileEntity createTileEntity(World world, int metadata)
	{
		switch(metadata)
		{
			case 0:
				return new TileEntityPressurizedTube();
			case 1:
				return new TileEntityUniversalCable();
			case 2:
				return new TileEntityMechanicalPipe();
			case 3:
				return new TileEntityLogisticalTransporter();
			case 4:
				return new TileEntityLogisticalTransporter();

			case 5:
				return new TileEntityDiversionTransporter();
			default:
				return null;
		}
	}
	
    @Override
    public void breakBlock(World world, int x, int y, int z, int i1, int i2)
    {
    	TileEntity tileEntity = world.getBlockTileEntity(x, y, z);
    	
    	if(!world.isRemote && tileEntity instanceof TileEntityLogisticalTransporter)
    	{
    		TileEntityLogisticalTransporter transporter = (TileEntityLogisticalTransporter)world.getBlockTileEntity(x, y, z);
    		
    		for(TransporterStack stack : transporter.transit)
    		{
    			TransporterUtils.drop(transporter, stack);
    		}
    	}
    	
    	super.breakBlock(world, x, y, z, i1, i2);
    }
	
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer entityplayer, int facing, float playerX, float playerY, float playerZ)
    {
    	if(world.isRemote)
    	{
    		return true;
    	}
    	
    	if(entityplayer.getCurrentEquippedItem() != null)
    	{
    		Item tool = entityplayer.getCurrentEquippedItem().getItem();
    		
	    	if(tool instanceof IToolWrench && ((IToolWrench)tool).canWrench(entityplayer, x, y, z))
	    	{
	    		if(entityplayer.isSneaking())
	    		{
	    			dismantleBlock(world, x, y, z, false);
	    		}
	    		
	    		return true;
	    	}
    	}
    	
    	return false;
    }
    
	public ItemStack dismantleBlock(World world, int x, int y, int z, boolean returnBlock) 
	{
		int meta = world.getBlockMetadata(x, y, z);
		ItemStack itemStack = new ItemStack(blockID, 1, meta);
        
        world.setBlockToAir(x, y, z);
        
        if(!returnBlock)
        {
            float motion = 0.3F;
            double motionX = (world.rand.nextFloat() * motion) + (1.0F - motion) * 0.5D;
            double motionY = (world.rand.nextFloat() * motion) + (1.0F - motion) * 0.5D;
            double motionZ = (world.rand.nextFloat() * motion) + (1.0F - motion) * 0.5D;
            
            EntityItem entityItem = new EntityItem(world, x + motionX, y + motionY, z + motionZ, itemStack);
	        
            world.spawnEntityInWorld(entityItem);
        }
        
        return itemStack;
	}
}

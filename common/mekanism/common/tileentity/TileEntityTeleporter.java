package mekanism.common.tileentity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mekanism.api.EnumColor;
import mekanism.api.Object3D;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.PacketHandler.Transmission;
import mekanism.common.Teleporter;
import mekanism.common.block.BlockMachine.MachineType;
import mekanism.common.network.PacketPortalFX;
import mekanism.common.network.PacketTileEntity;
import mekanism.common.util.ChargeUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.google.common.io.ByteArrayDataInput;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;
import dan200.computer.api.IPeripheral;

public class TileEntityTeleporter extends TileEntityElectricBlock implements IPeripheral
{
	/** This teleporter's frequency. */
	public Teleporter.Code code;
	
	public AxisAlignedBB teleportBounds = null;
	
	public Set<Entity> didTeleport = new HashSet<Entity>();
	
	public int teleDelay = 0;
	
	public boolean shouldRender;
	
	public boolean prevShouldRender;
	
	/** This teleporter's current status. */
	public String status = (EnumColor.DARK_RED + "Not ready.");
	
	public TileEntityTeleporter()
	{
		super("Teleporter", MachineType.TELEPORTER.baseEnergy);
		inventory = new ItemStack[1];
		code = new Teleporter.Code(0, 0, 0, 0);
	}
	
	@Override
	public void onUpdate()
	{
		super.onUpdate();
		
		if(teleportBounds == null)
		{
			resetBounds();
		}
		
		if(!worldObj.isRemote)
		{			
			if(Mekanism.teleporters.containsKey(code))
			{
				if(!Mekanism.teleporters.get(code).contains(Object3D.get(this)) && hasFrame())
				{
					Mekanism.teleporters.get(code).add(Object3D.get(this));
				}
				else if(Mekanism.teleporters.get(code).contains(Object3D.get(this)) && !hasFrame())
				{
					Mekanism.teleporters.get(code).remove(Object3D.get(this));
				}
			}
			else if(hasFrame())
			{
				ArrayList<Object3D> newCoords = new ArrayList<Object3D>();
				newCoords.add(Object3D.get(this));
				Mekanism.teleporters.put(code, newCoords);
			}
			
			switch(canTeleport())
			{
				case 1:
					status = EnumColor.DARK_GREEN + "Ready.";
					break;
				case 2:
					status = EnumColor.DARK_RED + "No frame.";
					break;
				case 3:
					status = EnumColor.DARK_RED + "No link found.";
					break;
				case 4:
					status = EnumColor.DARK_RED + "Links > 2.";
					break;
				case 5:
					status = EnumColor.DARK_RED + "Needs energy.";
					break;
				case 6:
					status = EnumColor.DARK_GREEN + "Idle.";
					break;
			}
			
			if(canTeleport() == 1 && teleDelay == 0)
			{
				teleport();
			}
			
			if(teleDelay == 0 && didTeleport.size() > 0)
			{
				cleanTeleportCache();
			}
			
			byte b = canTeleport();
			shouldRender = b == 1 || b > 4;
			
			if(shouldRender != prevShouldRender)
			{
				PacketHandler.sendPacket(Transmission.CLIENTS_RANGE, new PacketTileEntity().setParams(Object3D.get(this), getNetworkedData(new ArrayList())), Object3D.get(this), 40D);
			}
			
			prevShouldRender = shouldRender;
			
			teleDelay = Math.max(0, teleDelay-1);
		}
		
		ChargeUtils.discharge(0, this);
	}
	
	public void cleanTeleportCache()
	{
		List<Entity> list = worldObj.getEntitiesWithinAABB(Entity.class, teleportBounds);
		Set<Entity> teleportCopy = (Set<Entity>)((HashSet<Entity>)didTeleport).clone();
		
		for(Entity entity : teleportCopy)
		{
			if(!list.contains(entity))
			{
				didTeleport.remove(entity);
			}
		}
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side)
	{
		return new int[] {0};
	}
	
	@Override
	public boolean isItemValidForSlot(int slotID, ItemStack itemstack)
	{
		if(slotID == 0)
		{
			return ChargeUtils.canBeDischarged(itemstack);
		}
		
		return true;
	}
	
	public void resetBounds()
	{
		teleportBounds = AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord+1, yCoord+3, zCoord+1);
	}
	
	/**
	 * 1: yes
	 * 2: no frame
	 * 3: no link found
	 * 4: too many links
	 * 5: not enough electricity
	 * 6: nothing to teleport
	 * @return
	 */
	public byte canTeleport()
	{
		if(!hasFrame())
		{
			return 2;
		}
		
		if(!Mekanism.teleporters.containsKey(code) || Mekanism.teleporters.get(code).isEmpty())
		{
			return 3;
		}
		
		if(Mekanism.teleporters.get(code).size() > 2) 
		{
			return 4;
		}
		
		if(Mekanism.teleporters.get(code).size() == 2)
		{
			List<Entity> entitiesInPortal = getToTeleport();

			Object3D closestCoords = null;
			
			for(Object3D coords : Mekanism.teleporters.get(code))
			{
				if(!coords.equals(Object3D.get(this)))
				{
					closestCoords = coords;
					break;
				}
			}
			
			int electricityNeeded = 0;
			
			for(Entity entity : entitiesInPortal)
			{
				electricityNeeded += calculateEnergyCost(entity, closestCoords);
			}
			
			if(entitiesInPortal.size() == 0)
			{
				return 6;
			}
			
			if(getEnergy() < electricityNeeded)
			{
				return 5;
			}
			
			return 1;
		}
		
		return 3;
	}
	
	public void teleport()
	{
		if(worldObj.isRemote) return;
		
		List<Entity> entitiesInPortal = getToTeleport();

		Object3D closestCoords = null;
		
		for(Object3D coords : Mekanism.teleporters.get(code))
		{
			if(!coords.equals(Object3D.get(this)))
			{
				closestCoords = coords;
				break;
			}
		}
		
		for(Entity entity : entitiesInPortal)
		{						
			World teleWorld = FMLCommonHandler.instance().getMinecraftServerInstance().worldServerForDimension(closestCoords.dimensionId);
			TileEntityTeleporter teleporter = (TileEntityTeleporter)closestCoords.getTileEntity(teleWorld);
			
			if(teleporter != null)
			{
				teleporter.didTeleport.add(entity);
				teleporter.teleDelay = 5;
				
				if(entity instanceof EntityPlayerMP)
				{
					if(entity.worldObj.provider.dimensionId != closestCoords.dimensionId)
					{
						entity.travelToDimension(closestCoords.dimensionId);
					}
					
					((EntityPlayerMP)entity).playerNetServerHandler.setPlayerLocation(closestCoords.xCoord+0.5, closestCoords.yCoord+1, closestCoords.zCoord+0.5, entity.rotationYaw, entity.rotationPitch);
				}
				else {
					teleportEntityTo(entity, closestCoords, teleporter);
				}
				
				for(Object3D coords : Mekanism.teleporters.get(code))
				{
					PacketHandler.sendPacket(Transmission.CLIENTS_RANGE, new PacketPortalFX().setParams(coords), coords, 40D);
				}
				
				setEnergy(getEnergy() - calculateEnergyCost(entity, closestCoords));
				
				worldObj.playSoundAtEntity(entity, "mob.endermen.portal", 1.0F, 1.0F);
			}
		}
	}
	
	public void teleportEntityTo(Entity entity, Object3D coord, TileEntityTeleporter teleporter)
	{
		MinecraftServer server = MinecraftServer.getServer();
		WorldServer world = server.worldServerForDimension(coord.dimensionId);
		
		if(entity.worldObj.provider.dimensionId != coord.dimensionId)
		{
			entity.worldObj.removeEntity(entity);
			entity.isDead = false;
			
			world.spawnEntityInWorld(entity);
			entity.setLocationAndAngles(coord.xCoord+0.5, coord.yCoord+1, coord.zCoord+0.5, entity.rotationYaw, entity.rotationPitch);
			world.updateEntityWithOptionalForce(entity, false);
			entity.setWorld(world);
			world.resetUpdateEntityTick();
			
			Entity e = EntityList.createEntityByName(EntityList.getEntityString(entity), world);
			
			if(e != null)
			{
				e.copyDataFrom(entity, true);
				world.spawnEntityInWorld(e);
				teleporter.didTeleport.add(e);
			}
			
			entity.isDead = true;
		}
	}
	
	public List<Entity> getToTeleport()
	{
		List<Entity> entities = worldObj.getEntitiesWithinAABB(Entity.class, teleportBounds);
		List<Entity> ret = new ArrayList<Entity>();
		
		for(Entity entity : entities)
		{
			if(!didTeleport.contains(entity))
			{
				ret.add(entity);
			}
		}
		
		return ret;
	}
	
	@Override
	public void invalidate()
	{
		super.invalidate();
		
		if(!worldObj.isRemote)
		{
			if(Mekanism.teleporters.containsKey(code))
			{
				if(Mekanism.teleporters.get(code).contains(Object3D.get(this)))
				{
					Mekanism.teleporters.get(code).remove(Object3D.get(this));
				}
				
				if(Mekanism.teleporters.get(code).isEmpty()) 
				{
					Mekanism.teleporters.remove(code);
				}
			}
		}
	}
	
	public int calculateEnergyCost(Entity entity, Object3D coords)
	{
		int energyCost = 1000;
		
		if(entity.worldObj.provider.dimensionId != coords.dimensionId)
		{
			energyCost+=10000;
		}
		
		int distance = (int)entity.getDistance(coords.xCoord, coords.yCoord, coords.zCoord);
		energyCost+=(distance*10);
		
		return energyCost;
	}
	
	public boolean hasFrame()
	{
		if(isFrame(xCoord-1, yCoord, zCoord) && isFrame(xCoord+1, yCoord, zCoord)
				&& isFrame(xCoord-1, yCoord+1, zCoord) && isFrame(xCoord+1, yCoord+1, zCoord)
				&& isFrame(xCoord-1, yCoord+2, zCoord) && isFrame(xCoord+1, yCoord+2, zCoord)
				&& isFrame(xCoord-1, yCoord+3, zCoord) && isFrame(xCoord+1, yCoord+3, zCoord)
				&& isFrame(xCoord, yCoord+3, zCoord)) {return true;}
		if(isFrame(xCoord, yCoord, zCoord-1) && isFrame(xCoord, yCoord, zCoord+1)
				&& isFrame(xCoord, yCoord+1, zCoord-1) && isFrame(xCoord, yCoord+1, zCoord+1)
				&& isFrame(xCoord, yCoord+2, zCoord-1) && isFrame(xCoord, yCoord+2, zCoord+1)
				&& isFrame(xCoord, yCoord+3, zCoord-1) && isFrame(xCoord, yCoord+3, zCoord+1)
				&& isFrame(xCoord, yCoord+3, zCoord)) {return true;}
		return false;
	}
	
	public boolean isFrame(int x, int y, int z)
	{
		return worldObj.getBlockId(x, y, z) == Mekanism.basicBlockID && worldObj.getBlockMetadata(x, y, z) == 7;
	}
	
	@Override
    public void readFromNBT(NBTTagCompound nbtTags)
    {
        super.readFromNBT(nbtTags);

        code.digitOne = nbtTags.getInteger("digitOne");
        code.digitTwo = nbtTags.getInteger("digitTwo");
        code.digitThree = nbtTags.getInteger("digitThree");
        code.digitFour = nbtTags.getInteger("digitFour");
    }

	@Override
    public void writeToNBT(NBTTagCompound nbtTags)
    {
        super.writeToNBT(nbtTags);
        
        nbtTags.setInteger("digitOne", code.digitOne);
        nbtTags.setInteger("digitTwo", code.digitTwo);
        nbtTags.setInteger("digitThree", code.digitThree);
        nbtTags.setInteger("digitFour", code.digitFour);
    }
	
	@Override
	public void handlePacketData(ByteArrayDataInput dataStream)
	{
		if(!worldObj.isRemote)
		{
			if(Mekanism.teleporters.containsKey(code))
			{
				if(Mekanism.teleporters.get(code).contains(Object3D.get(this)))
				{
					Mekanism.teleporters.get(code).remove(Object3D.get(this));
				}
				
				if(Mekanism.teleporters.get(code).isEmpty()) Mekanism.teleporters.remove(code);
			}
			
			int type = dataStream.readInt();
			
			if(type == 0)
			{
				code.digitOne = dataStream.readInt();
			}
			else if(type == 1)
			{
				code.digitTwo = dataStream.readInt();
			}
			else if(type == 2)
			{
				code.digitThree = dataStream.readInt();
			}
			else if(type == 3)
			{
				code.digitFour = dataStream.readInt();
			}
			return;
		}
		
		super.handlePacketData(dataStream);
		
		status = dataStream.readUTF().trim();
		code.digitOne = dataStream.readInt();
		code.digitTwo = dataStream.readInt();
		code.digitThree = dataStream.readInt();
		code.digitFour = dataStream.readInt();
		shouldRender = dataStream.readBoolean();
	}
	
	@Override
	public ArrayList getNetworkedData(ArrayList data)
	{
		super.getNetworkedData(data);
		
		data.add(status);
		data.add(code.digitOne);
		data.add(code.digitTwo);
		data.add(code.digitThree);
		data.add(code.digitFour);
		data.add(shouldRender);
		
		return data;
	}
	
	@Override
	public boolean canExtractItem(int slotID, ItemStack itemstack, int side)
	{
		return ChargeUtils.canBeOutputted(itemstack, false);
	}

	@Override
	public String getType()
	{
		return getInvName();
	}

	@Override
	public String[] getMethodNames()
	{
		return new String[] {"getStored", "canTeleport", "getMaxEnergy", "getEnergyNeeded", "teleport", "set"};
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws Exception
	{
		switch(method)
		{
			case 0:
				return new Object[] {getEnergy()};
			case 1:
				return new Object[] {canTeleport()};
			case 2:
				return new Object[] {getMaxEnergy()};
			case 3:
				return new Object[] {(getMaxEnergy()-getEnergy())};
			case 4:
				teleport();
				return new Object[] {"Attempted to teleport."};
			case 5:
				if(!(arguments[0] instanceof Integer) || !(arguments[1] instanceof Integer))
				{
					return new Object[] {"Invalid parameters."};
				}
				
				int digit = (Integer)arguments[0];
				int newDigit = (Integer)arguments[1];
				
				switch(digit)
				{
					case 0:
						code.digitOne = newDigit;
						break;
					case 1:
						code.digitTwo = newDigit;
						break;
					case 2:
						code.digitThree = newDigit;
						break;
					case 3:
						code.digitFour = newDigit;
						break;
					default:
						return new Object[] {"No digit found."};
				}
			default:
				System.err.println("[Mekanism] Attempted to call unknown method with computer ID " + computer.getID());
				return new Object[] {"Unknown command."};
		}
	}

	@Override
	public boolean canAttachToSide(int side)
	{
		return true;
	}
	
	@Override
	public void attach(IComputerAccess computer) {}

	@Override
	public void detach(IComputerAccess computer) {}
	
	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox()
	{
		return INFINITE_EXTENT_AABB;
	}
}

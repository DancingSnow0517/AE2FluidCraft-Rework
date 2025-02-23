package com.glodblock.github.common.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;

import com.glodblock.github.common.item.ItemFluidPacket;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;

public class TileFluidPacketDecoder extends AENetworkTile
        implements IGridTickable, IAEAppEngInventory, IInventory, IFluidHandler {

    private final AppEngInternalInventory inventory = new AppEngInternalInventory(this, 1);
    private final BaseActionSource ownActionSource = new MachineSource(this);

    @Reflected
    public TileFluidPacketDecoder() {
        getProxy().setIdlePowerUsage(1D);
        getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    public IInventory getInventory() {
        return inventory;
    }

    @Override
    public boolean canBeRotated() {
        return false;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(5, 120, false, true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        ItemStack stack = inventory.getStackInSlot(0);
        if (stack == null || !(stack.getItem() instanceof ItemFluidPacket)) {
            return TickRateModulation.SLEEP;
        }
        FluidStack fluid = ItemFluidPacket.getFluidStack(stack);
        if (fluid == null || fluid.amount <= 0) {
            inventory.setInventorySlotContents(0, null);
            return TickRateModulation.SLEEP;
        }
        IAEFluidStack aeFluid = AEFluidStack.create(fluid.copy());
        IEnergyGrid energyGrid = node.getGrid().getCache(IEnergyGrid.class);
        IMEMonitor<IAEFluidStack> fluidGrid = node.getGrid().<IStorageGrid>getCache(IStorageGrid.class)
                .getFluidInventory();
        IAEFluidStack remaining = Platform.poweredInsert(energyGrid, fluidGrid, aeFluid, ownActionSource);
        if (remaining != null) {
            if (remaining.getStackSize() == aeFluid.getStackSize()) {
                inventory.setInventorySlotContents(0, ItemFluidPacket.newStack(remaining.getFluidStack()));
                return TickRateModulation.SLOWER;
            }
            inventory.setInventorySlotContents(0, ItemFluidPacket.newStack(remaining.getFluidStack()));
            return TickRateModulation.FASTER;
        } else {
            inventory.setInventorySlotContents(0, null);
            return TickRateModulation.SLEEP;
        }
    }

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removedStack,
            ItemStack newStack) {
        try {
            getProxy().getTick().alertDevice(getProxy().getNode());
        } catch (GridAccessException e) {
            // NO-OP
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public NBTTagCompound writeToNBTEvent(NBTTagCompound data) {
        inventory.writeToNBT(data, "Inventory");
        return data;
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBTEvent(NBTTagCompound data) {
        inventory.readFromNBT(data, "Inventory");
    }

    @Override
    public int getSizeInventory() {
        return inventory.getSizeInventory();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        return inventory.decrStackSize(slot, amount);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return inventory.getStackInSlotOnClosing(slot);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inventory.setInventorySlotContents(slot, stack);
    }

    @Override
    public String getInventoryName() {
        return inventory.getInventoryName();
    }

    @Override
    public boolean hasCustomInventoryName() {
        return inventory.hasCustomInventoryName();
    }

    @Override
    public int getInventoryStackLimit() {
        return inventory.getInventoryStackLimit();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return inventory.isUseableByPlayer(player);
    }

    @Override
    public void openInventory() {
        inventory.openInventory();
    }

    @Override
    public void closeInventory() {
        inventory.openInventory();
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack.getItem() instanceof ItemFluidPacket;
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack requestFluid, boolean doDrain) {
        return this.drain(requestFluid, doDrain);
    }

    private FluidStack drain(FluidStack requestFluid, boolean doDrain) {
        if (requestFluid == null || requestFluid.amount <= 0) return null;
        ItemStack fluidPacket = this.inventory.getStackInSlot(0);
        if (fluidPacket != null) {
            FluidStack fs = ItemFluidPacket.getFluidStack(fluidPacket);
            if (fs == null) return null;
            if (fs.isFluidEqual(requestFluid)) {
                if (fs.amount > requestFluid.amount) {
                    fs.amount -= requestFluid.amount;
                    if (doDrain) this.inventory.setInventorySlotContents(0, ItemFluidPacket.newStack(fs));
                    return requestFluid;
                } else {
                    if (doDrain) this.inventory.setInventorySlotContents(0, null);
                    return fs;
                }
            }
        }
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        ItemStack fluidPacket = this.inventory.getStackInSlot(0);
        if (fluidPacket != null) {
            FluidStack requestFluid = ItemFluidPacket.getFluidStack(fluidPacket);
            if (requestFluid == null) return null;
            requestFluid.amount = maxDrain;
            return this.drain(requestFluid, doDrain);
        }
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return false;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        ItemStack fluidPacket = this.inventory.getStackInSlot(0);
        if (fluidPacket == null) {
            return new FluidTankInfo[0];
        } else {
            FluidStack fs = ItemFluidPacket.getFluidStack(fluidPacket);
            if (fs == null) return new FluidTankInfo[0];
            return new FluidTankInfo[] { new FluidTankInfo(fs, fs.amount) };
        }
    }
}

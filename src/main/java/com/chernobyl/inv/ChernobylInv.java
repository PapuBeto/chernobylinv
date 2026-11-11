package com.chernobyl.inv;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(ChernobylInv.MODID)
public class ChernobylInv {
    public static final String MODID = "chernobylinv";

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    /**
     * Menú único del inventario. El contenedor pasado a {@link InvMenu} es
     * un marcador vacío para los slots de mochila reservados; el owner
     * sustituirá la lógica de esos slots por su propio sistema.
     *
     * <p>El loot de Survivor's Arsenal y el crafteo de etapa 4 quedan
     * aparcados y ya no se registran menús especiales para ellos.</p>
     */
    public static final RegistryObject<MenuType<InvMenu>> INV_MENU =
            MENUS.register("inv", () -> IForgeMenuType.create(
                    (id, inv, data) -> new InvMenu(id, inv, new SimpleContainer(BackpackView.SIZE))));

    public ChernobylInv() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        MENUS.register(bus);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }
}

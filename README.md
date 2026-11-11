# ChernobylInv

Mod para **Minecraft 1.20.1 (Forge 47.4.28)** que rediseña por completo el
inventario y el HUD con estética industrial de supervivencia: carbón, gris,
cian y verde, con animaciones suaves y una identidad visual propia.

Está pensado como base de un sistema de inventario/crafteo de etapa 4 para un
server modpack (CHERNOBYL) y está listo para que otro desarrollador lo conecte
con su propio mod de mochilas o de crafteo.

> ⚠️ Este proyecto es un mod independiente. No es compatible con el mod de
> inventario vanilla de Minecraft ni con Inventory Tweaks.

---

## Características

- **Inventario custom**: se abre con `E`.
- **Menú de tres zonas**: izquierda personaje + armadura, centro inventario +
  mochila, derecha crafteo/catálogo.
- **Personaje 3D en pantalla** que mira hacia el ratón (builtin de Minecraft).
- **Transición animada** entre inventario completo ↔ detalle de receta:
  el personaje desaparece, el inventario se desplaza a la izquierda, el
  catálogo se desplaza al centro y el detalle se abre a la derecha.
- **Sistema de recetas por JSON** (`config/chernobylinv-recipes.json`) con
  cantidades por ingrediente, categorías, tiempos y límites por orden.
- **Cola de fabricación de hasta 10 órdenes** en servidor, persistente entre
  sesiones, con cancelación y reembolso de materiales.
- **Objetos grandes compatibles**: shift-click, drag & drop entre inventario,
  cinturón, mochila y armadura.
- **HUD custom** con vida, sed, comida, energía y radiación, en dos estilos:
  **CENTRO** (compacto) y **ESQUINA** (panel), cambiable desde Escape → botón
  **HUD**.
- **Geiger** dentro del inventario y en el HUD (lee el mod `radioactive`).
- **Indicador de crafteo activo** sobre el HUD: muestra el objeto en
  fabricación y la barra de progreso.
- **Hotbar de 6 slots** con HUD vanilla oculto, y bolsillos en el inventario
  para los 3 slots que no caben.
- **Integración opcional** con Radioactive, Thirst, ParCool, Sophisticated
  Backpacks y TACZ.
- **Sin blur** en el fondo (actual); el mundo queda atenuado (oscurecido).

---

## Requisitos

- JDK 17
- Minecraft 1.20.1 / Forge 47.4.28
- (Opcional para dev) los mods en `libs/` ya están versionados en el repo:
  - `curios-forge-5.14.1+1.20.1.jar` (dependencia de otros)
  - `sophisticatedcore` + `sophisticatedbackpacks` (mochila actual)
  - `geckolib-forge-1.20.1-4.8.4.jar`
  - `ThirstWasTaken` (barra de sed)
  - `ParCool` (barra de energía)
  - `radioactive-0.4.6.1.jar` (geiger)
  - `tacz-1.20.1-1.1.8-hotfix.jar` (HUD de armas)
  - `sa_decor` / `sa_essentials` / `sa_lib` (loot Survivor's Arsenal,
    actualmente **desactivado**)

---

## Compilar y ejecutar

```powershell
# Windows
.\gradlew.bat build

# Cliente de desarrollo
.\gradlew.bat runClient

# Solo compilar
.\gradlew.bat compileJava processResources
```

El JAR resultante está en `build/libs/chernobylinv-1.0.0.jar`.

---

## Arquitectura

### Mapa de clases

| Clase | Rol |
|---|---|
| `ChernobylInv` | Punto de entrada del mod. Registra el menú y el canal de red. Abre el inventario con `OpenInvPacket`. |
| `InvMenu` | Menú del inventario: armadura (4), inventario principal (27), cinturon (6), bolsillos (3), mochila (27 reservados). `stillValid` siempre true; `quickMoveStack` implementa shift-click. |
| `InvScreen` | UI del inventario. Dibuja los tres paneles, la transición animada al seleccionar receta, el botón "Ver todos los crafteos" y los bolsillos/cinturón. |
| `CraftCatalogScreen` | Catálogo completo de recetas. Búsqueda, filtro por categoría, 5×4 grid con paginación y panel de detalle con botón FABRICAR. |
| `CraftingRenderer` | Helpers compartidos de renderizado: paneles, slots, wrap de texto, contadores de materiales, botón `FABRICAR`, etc. |
| `RecipeDatabase` | Carga `config/chernobylinv-recipes.json`, valida IDs (`modid:nombre`), sincroniza resumen al cliente. Toda la lógica de consumir/devolver materiales. |
| `CraftingManager` | Lógica servidor: valida, consume, encola (10 órdenes máx), guarda en `SavedData`, entrega a inventario/mochila/salida al terminar. |
| `CraftingQueue` | Estado de cola por jugador (entries + almacenamiento de salida). |
| `CraftingSave` | `SavedData` de Minecraft que persiste la cola y la salida pendiente entre sesiones y reinicios. |
| `ItemDatabase` | Resolución `modid:nombre` → `Item` del registro de Forge. |
| `BackpackCompat` | **Interfaz** de capa de mochilas: `handler(Player)` devuelve el `IItemHandlerModifiable` de la mochila o null. Hoy apuntada a Sophisticated. |
| `SophisticatedBackpackCompat` | Implementación actual de `BackpackCompat` para Sophisticated Backpacks. |
| `BackpackAccess` | Acceso directo a la capability de Sophisticated (pechera). |
| `BackpackView` | Vista `Container` de la mochila: ventana deslizable 9×3 sobre la capability, sincronizada al cliente con `DataSlot`. |
| `StatsHud` | HUD de stats (vida, sed, comida, energía, radiación) y el indicador de crafteo flotante. |
| `HudLayout` | Reload listener que lee `hud.json` y aplica presets CENTRO/ESQUINA; persiste la elección en `config/chernobylinv-hud.properties`. |
| `HudConfigScreen` | Pantalla accesible desde el menú de pausa → botón **HUD** para elegir modo. |
| `RadCompat` | Compatibilidad con el mod `radioactive`: detecta geiger (mano, hotbar, inventario, curios) y lee dosis/tasa. |
| `RadHudEvents` | Oculta HUD propio del mod de radiación (el de geiger). |
| `ParCoolCompat` / `ThirstCompat` | Lectura de energía y sed. |
| `RecipeDatabase` (cliente) | `ClientCraftingCache` guarda la cola recibida del servidor para que el HUD y la UI la dibujen. |

### Flujo de apertura

```
E (cliente) → OpenInvPacket → servidor → NetworkHooks.openScreen(inv)
→ InvMenu (slots fijos) → InvScreen (con transición animada al abrir recetas)
```

El inventario cosmético no sincroniza objetos por sí mismo; todo pasa por el
`AbstractContainerMenu` vanilla (`InvMenu`), por lo que el juego mueve el
`ItemStack`real y funciona drag & drop, shift-click y soltado de stacks
normalmente. Donde hace falta control especial (mochila), se usan capas
`BackpackView`/`BackpackAccess`/`BackpackCompat`.

### Animación de paneles

`InvScreen` no mueve los slots de verdad; el movimiento se hace con **alpha
y posiciones de dibujo** interpoladas a un `transitionT` 0 → 1. Los slots se
recolocan en el menú con el Mixin `SlotAccessor` solo durante la transición
(para que el mouse y el drag & drop los sigan viendo en el sitio nuevo).

---

## Configuración

### `config/chernobylinv-recipes.json`

Formato propio, independiente de las recetas vanilla. Permite cantidades por
ingrediente y categorías. Ejemplo:

```json
{
  "queueMax": 10,
  "categories": [
    {"id":"Tools","name":"Herramientas"},
    {"id":"Weapons","name":"Armas"}
  ],
  "recipes": [
    {
      "id": "stone_hatchet",
      "output": "minecraft:stone_hatchet",
      "name": "Wet derped axe",
      "description": "Rapida shilla y corta",
      "category": "Tools",
      "craftingTime": 15,
      "craftingAmount": 1,
      "maxCraftAmount": 10,
      "ingredients": [
        {"itemId": "minecraft:oak_planks", "amount": 2},
        {"itemId": "minecraft:cobblestone", "amount": 3}
      ],
      "requirements": {"workbenchLevel": 0},
      "enabled": true,
      "order": 1
    }
  ]
}
```

- **`id`**: clave única. **`output`**: `modid:nombre`. Si falta, se usa el id.
- **`ingredients[].itemId`**: `modid:nombre`; objetos de otros mods también
  funcionan (`tacz:ak47`, etc.).
- **`craftingTime`**: segundos por unidad. **`maxCraftAmount`**: máximo por click.
- **`requirements.workbenchLevel`**: preparado para futuras mesas; hoy se fuerza
  a `0`.
- **`enabled`**: desactivar una receta la oculta sin borrarla.
- **`order`**: orden de aparición en el catálogo.

Si un `itemId` no existe se `warn`ea y se omite esa receta; el resto sigue cargando.

### `config/chernobylinv-hud.properties`

Creado automáticamente al elegir modo. Contiene `mode=center` o `mode=corner`.
Tiene prioridad sobre `hud.json` de los recursos.

---

## Integración con otros mods

### Geiger / radiación
- Interfaces opcionales; el mod funciona sin Radioactive instalado.
- Si está, `RadCompat` detecta geiger en mano, hotbar, inventario o curio slot
  y lee `ClientRadiationState` para mostrar dosis/tasa.
- Su HUD aparte se oculta con `RadHudEvents`.

### Thirst / ParCool
- Barras de sed y energía se leen desde sus capabilities.
- Si faltan, no se dibujan.

### Sophisticated Backpacks (mochila ahora)
- `BackpackAccess.handler(player)` devuelve el `IItemHandlerModifiable` de la
  mochila si está equipada en la pechera, o null.
- `BackpackView` es una vista `Container` sobre esa capability, 27 slots con
  offset de fila; se dibuja en el inventario y se sincroniza al cliente.

### TACZ (desde 1.1.8)
- Añadido como `runtimeOnly` para dev y dependencia opcional `[1.1.8,)` en `mods.toml`.
- Su HUD no se dibuja sobre nuestro inventario porque `ClientForgeEvents.onOverlayPre`
  cancela **todos** los overlays cuando `InvScreen` está abierto.
- Comentario: TACZ en su `mods.toml` requiere `shouldersurfing` con ordering;
  si no lo tienes, TACZ no cargará.

### Survivor's Arsenal (SA) — actualmente desactivado
- `LootView` + `NetworkHooksMixin` (en `mixin`) sustituían la apertura del
  cofre de loot por nuestro inventario.
- Ahora el mixin es un stub vacío y `chernobylinv.mixins.json` tiene
  `mixins: []`. SA se sigue cargando en dev pero no interfiere.

---

## Cómo hacer que otro mod de mochila funcione (el caso clave)

El objetivo es que la mochila del mod del owner encaje en el mismo hueco que
la de Sophisticated. Hay **dos niveles**:

### Opción A — reutilizar nuestra capa (recomendada)
1. Implementa una nueva marca de `BackpackCompat`:
   ```java
   public final class MiBackpackCompat implements BackpackCompat {
       public static final MiBackpackCompat INSTANCE = new MiBackpackCompat();
       private MiBackpackCompat() {}
       @Override
       public IItemHandlerModifiable handler(Player player) {
           // ejemplo:
           return player.getCapability(MiBackpackCapability.HANDLING)
                   .map(w -> (IItemHandlerModifiable) w.getInventoryHandler())
                   .orElse(null);
       }
   }
   ```
2. Apunta `BackpackCompat.current()` a tu implementación:
   ```java
   static BackpackCompat current() { return MiBackpackCompat.INSTANCE; }
   ```
3. Listo. `BackpackView`, el menú, el `InvMenu`, el pickup y el HUD ya
   funcionan con tu capability; no hay que tocar más.
4. Opcionalmente, si tu mochila expone **capacidad nativa**, envuelve con
   `IItemHandlerModifiable` o `IItemHandler` + `ItemHandlerHelper` como hoy.

### Opción B — menú propio del owner
Si el owner quiere su propia UI de mochila en vez de la nuestra:
1. Registra un nuevo `MenuType<>` en `ChernobylInv.MENUS`.
2. Haz que su packet abra ese menú en vez de `INV_MENU`.
3. Haz que la pantalla se dibuje como quiera.

### Qué no tocar si haces la Opción A
- `InvMenu` (posiciones de slots 40-66 reservadas para mochila).
- `BackpackView` (ventana 9×3 con scroll).
- `BackpackAccess` (Sophisticated) — quítalo o déjalo sin uso.
- `CommonForgeEvents` (pickup automático) — hoy **desactivado** porque
  ahora el owner decide cómo se guarda; puedes reactivarlo llamando al mismo
  helper `inventoryCanAccept` + tu capability.

### Receta para el owner
1. Implementar `handler(Player)` para su capability de mochila.
2. (Opcional) modificar tamaños en `BackpackView` (`COLS`, `ROWS`, `SIZE`) y
   las coordenadas de slots en `InvMenu` (`BACK_START..BACK_END`) si el número
   de slots llega a ser mayor de 27 (hoy ventana 9×3 fija). Recuerda actualizar
   `ClientCraftingCache`, que también usa solo 9 slots visibles.
3. Cambiar `SophisticatedBackpackCompat` por `MiBackpackCompat`.
4. Probar shift-click de mochila → inventario y viceversa, y el pickle de un
   objeto con la mochila puesta.

### Externas
- Las recetas que quiera anunciar el mod del owner deben ir en
  `chernobylinv-recipes.json`. No se admiten recetas vanilla configuradas en
  `data/<modid>/recipes`; nuestro crafteo es independiente de la mesa de
  trabajo.

---

## Cómo probar todo

```powershell
.\gradlew.bat runClient
```

1. Entra al mundo y pulsa `E`.
2. Prueba arrastrar objetos entre inventario ↔ cinturón ↔ bolsillos ↔ armadura ↔ mochila.
3. Abre el catálogo con **Ver todos los crafteos**.
4. Selecciona una receta, pulsa **FABRICAR**, cierra el inventario y espera el
   tiempo configurado. Observa el indicador flotante en el HUD y prueba el
   botón de cancelar en la cola.
5. Prueba el modo CENTRO/ESQUINA desde Escape → **HUD**.
6. Si el geiger está equipado y Radioactive cargado, el HUD muestra dosis/tasa.

---

## Problemas conocidos / pendiente

- El contador de materiales en el UI solo cuenta el inventario visible; el del
  servidor sí cuenta la mochila vía capability. Marcará rojo aunque la mochila
  tenga los materiales.
- Los bolsillos en el inventario aún no están optimizados visualmente al 100 %;
  la distribución actual es funcional y uniforme pero puede pulirse más.
- La mochila hoy es Sophisticated fijo vía `SophisticatedBackpackCompat`.
- SA loot desactivado; si se reactiva, hay que revisar el registro del mixin
  (Forge 1.20.1 **requiere** `MixinConfigs` en el `MANIFEST.MF`, ya incluido).
- El inventario actualmente no usa `GuiGraphics` blur; el fondo solo se oscurece.
- TACZ requiere `shouldersurfing` en su mods.toml para poder cargar.
- HUD del Geiger dentro del inventario muestra lecturas aproximadas; no controla
  la barra fija del VU del geiger de Radioactive, que lleva su propio HUD.

---

## Notas

- `.gitignore` excluye `build/`, `run/`, `.gradle/` y logs; no subas artefactos.
- Este mod usa **Mojang official mappings** (`mapping_channel=official`).
- Los JARs de dependencias viven en `libs/` y están versionados intencionadamente
  para que el otro dev pueda compilar sin depender de un repositorio externo.

## Licencia y créditos

Código bajo All Rights Reserved (ver `LICENSE.txt`). Estructura de Forge MDK
heredada de MinecraftForge (LGPL 2.1 para los componentes de Forge).

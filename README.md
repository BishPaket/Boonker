# Bunker & Radiation (modid `bunker`)

Мод для нового сезона Shards of Void по ТЗ «Радиация, костюм и система бункеров», версия 2,
и ТЗ v3 «Доработка бункеров — энергия, расширение и жизнеобеспечение».
Minecraft 1.21.1, NeoForge 21.1.248+ (сборка против 21.1.267), Java 21. Всё в одном моде.

## Сборка

```
./gradlew build          # jar появится в build/libs/bunker-1.0.0.jar
./gradlew runClient      # тестовый клиент
./gradlew runServer      # тестовый сервер
```

В `.github/workflows/build.yml` есть сборка в GitHub Actions: после push jar лежит в артефактах.

`smoke.yml` (запуск вручную) поднимает выделенный сервер и проверяет, что мод, датапак и миксины грузятся без ошибок.

## Что где

| Раздел ТЗ | Код |
|---|---|
| 1. Радиация | `radiation/RadiationSystem` (проверка раз в N тиков, доза, стадии, урон, износ, синхронизация), `RadiationMath`, `RadiationCommands` |
| 1.4 Wastelands | `compat/WastelandsCompat` + миксин `mixin/compat/WastelandsRadiationMixin` |
| 1.5 HUD, Гейгер | `client/RadiationHud`, `client/GeigerClicker` |
| 2. Костюм | `item/RadSuitItem`, `item/RadSuitUpgradeRecipe`, `mixin/SmithingMenuMixin` |
| 3. Бункерный металл | `registry/ModBlocks`, защита от взрывов в `bunker/BunkerEvents.onDetonate` + `mixin/ExplosionMixin`, торнадо — `mixin/compat/TornadoHelperMixin`, `WeatherUtilMixin` |
| 4. Ядро | `block/BunkerCoreBlock`, `item/BunkerCoreItem`, `bunker/ActivationConditions`, выдача — `BunkerEvents.onLogin` |
| 5. Герметичность | `bunker/BunkerValidator` (заливка), `bunker/BunkerGeometry`, `bunker/BunkerManager` (индексы, очередь, зоны) |
| 6. Комнаты | `block/BunkerRoomBlock`, `item/BunkerRoomItem`, `BunkerManager.checkRoomPlacement` |
| 7. Улучшения | `bunker/BunkerUpgrades` (данные), `bunker/BunkerActions` (кнопки) |
| 8. Данные, сеть, конфиг | `bunker/BunkerSavedData`, `registry/ModAttachments`, `registry/ModComponents`, `network/*`, `config/*` |
| v3 1. Области, расширение, размещение | `bunker/Area`, `bunker/BunkerGeometry` (общая грань, проходы), `BunkerManager.roomVariants/checkBounds` |
| v3 2. Гермодверь и люк | `block/BunkerDoorBlock`, `block/BunkerHatchBlock`, `block/Airlocks`, шлюз — `BunkerManager.pollOpenables` |
| v3 3–5. Энергия | `energy/EnergySystem` (сеть, баланс, отключение по приоритету), `energy/*BlockEntity`, `block/MachineBlock`, `block/PowerCableBlock` |
| v3 6. Интерфейс | `bunker/BunkerActions` (вкладки и действия), `client/BunkerScreen`, `client/MachineScreen`, `menu/MachineMenu` |
| v3 7. Станция костюмов | `energy/SuitStationBlockEntity`, `client/SuitStationRenderer` |
| v3 8. Жизнеобеспечение | `energy/LifeSupport`, `AirFilterUnitBlockEntity`, `WaterPurifierBlockEntity`, эффекты — `RadiationSystem.applyLifeSupport`, «Обезвоживание» — `registry/ModEffects` + `mixin/FoodDataMixin` |
| v3 5.2, 8.6 Лут | `loot/AddPartialItemModifier`, `data/bunker/loot_modifiers/*.json` |

## Конфиги

* `world/serverconfig/bunker-server.toml` — все ключи раздела 8.3 (`radiation.*`, `suit.*`, `bunker.*`, `bunkerMetal.*`, `room.*`, `upgrade.*`)
  плюс дополнительные: максимум дозы, пороги стадий, эффекты стадий, таблица `depthMultipliers`, погодные множители,
  доли частей костюма и коэффициенты уровней, период износа, `wastelandsOverride`, `bunkerMetal.blockFakePlayers`,
  `bunker.validation.perTick`, `bunker.highlightSeconds`.
  `/radiation rate` и `/radiation enable|disable` пишут прямо в этот файл.
* `config/bunker-startup.toml` — параметры ArmorMaterial костюма (броня, прочность). Читается до регистрации предметов,
  поэтому должен совпадать на сервере и у клиентов.
* `config/bunker-client.toml` — положение HUD (по умолчанию справа сверху под иконками эффектов, First Aid — слева) и громкость счётчика.

## Датапак

* `data/bunker/recipe/` — металл, блок комнат, шаблон, части костюма, улучшения костюма (`bunker:radsuit_upgrade`).
* `data/bunker/bunker_upgrades/core.json`, `room.json` — стоимость и эффекты уровней. Эффекты: `max_rooms`,
  `dose_decay` (снятие дозы в объёме, ед./с), `mob_effects` (эффекты игрокам в объёме), v3: `generation` (FE/t ядра),
  `expansion` (бюджет расширения), `max_batteries`. Принимаются и camelCase-имена. Файл из другого namespace перекрывает встроенный.
* `data/bunker/loot_modifiers/` + `data/neoforge/loot_modifiers/global_loot_modifiers.json` — ячейки (Древний город 25%, темница 20%,
  шахта 15%, портал и кораблекрушение 10%, заряд 0–60%) и фильтры (10%, частично изношены). Тип `bunker:add_partial_item`:
  `item`, `chance`, `min`, `max` + любые условия (`neoforge:loot_table_id`) — так добавляются структуры из других модов.
* Теги v3: `bunker:airlocks` (шлюзовой режим), `bunker:surface_energy` (защищаются от взрывов и торнадо только при
  `energy.surfaceTornadoImmune`). Гермодверь, люк, энерговвод и ФВУ входят в `shell_blocks` и `indestructible`.
* `data/bunker/damage_type/stale_air.json` — урон спёртым воздухом, обходит броню, для First Aid — как радиация.
* Теги: `bunker:shell_blocks` (оболочка; можно добавлять двери/люки — считаются оболочкой только закрытыми),
  `bunker:indestructible` (не ломаются взрывами, торнадо и «фейковыми игроками»), `bunker:radsuit`.
* `data/bunker/damage_type/radiation.json` + тег `minecraft:bypasses_armor`.
* `data/bunker/firstaid/damage_distributions/radiation.json` — First Aid распределяет урон радиации равномерно по всем частям тела.
* `data/bunker/block_armor/*.json` — снаряды Create Big Cannons не пробивают блоки мода.

## Команды

* `/radiation get|set|add <игрок> [значение]`, `/radiation rate <значение>`, `/radiation enable|disable` — уровень 2.
* `/bunker give|resetcore <игрок>`, `/bunker setlevel <игрок> core|room <1-3> [pos]`, `/bunker info <игрок>`, `/bunker revalidate` — уровень 2.
* `/bunker trust|untrust <игрок>` — владелец; доверенные нужны для `bunkerMetal.ownerOnlyBreak`.

## Решения, принятые там, где ТЗ допускало варианты

1. **Wastelands.** Миксин отключает его собственный тик радиации (начисление по небу/поверхности, спад, симптомы).
   Дозу ведёт этот мод (double в attachment `bunker:radiation_dose`), а шкала Wastelands 0–100 зеркалится:
   если Rad-Away или команда Wastelands меняют шкалу, разница переносится в нашу дозу. Rad-Away, счётчик Гейгера
   Wastelands и его команды продолжают работать. Бонус от блоков радиоактивных отходов Wastelands при этом тоже выключен.
   Эффекты стадий — стартовый набор из ТЗ (настраивается в конфиге), потому что симптомы Wastelands завязаны на его шкалу.
2. **Активация бункера** требует и полной оболочки (все ожидаемые блоки на месте), и успешной заливки. Иначе снятие
   углового или рёберного блока не ломало бы герметичность: при заливке по 6 направлениям такие блоки не граничат с внутренним объёмом.
3. **Над ядром** проверяется `canSeeSky(pos.above())`: само ядро непрозрачное, `canSeeSky(pos)` всегда давал бы «под землёй».
4. **Блок комнат**: в сетке 3×3 не помещаются 8 металла + 2 алмаза, стартовый рецепт — 7 металла + 2 алмаза.
5. **Улучшение костюма II → III**: в кузнечном столе один слот добавки, поэтому незерит кладётся в слот, а 2 алмаза
   списываются из инвентаря (`extra_cost`). I → II — 2 алмаза в слоте (`addition_count`).
6. **Рецепты частей костюма** в ТЗ не заданы — добавлены стартовые (кожа + железо, шлем со стеклом).
7. **Сломанная часть костюма** не исчезает (как элитры): прочность останавливается на 1, часть не даёт ни радиационной,
   ни физической защиты, чинится железом на наковальне.
8. **Снятый владельцем блок комнат** выпадает с привязкой и уровнем — переставить комнату можно без новой привязки.
9. Подсветка рисуется поверх стен, чтобы её было видно под землёй.

### v3

10. **Чужой FE** через энерговвод разрешён с потолком 40 FE/t (`energy.acceptForeignFE = true`, `foreignMaxRate = 40`).
11. **Ремонт на наковальне** выключен (`suit.anvilRepair = false`): часть костюма чинится только станцией. Переименование и книги работают.
12. **Ядро I** даёт 35 FE/t (II — 50, III — 80): ровно область ядра и две базовые комнаты.
13. **Мода жажды в сборке нет**, поэтому «Обезвоживание» — свой эффект: без естественной регенерации, Голод I, через 10 минут Слабость I.
    Бутылку чистой воды можно пить — эффект снимается, пока бак снова пуст.
14. **Ветряк** не сделан — это этап 2 по ТЗ.
15. **Запас воды пуст и без станции водоочистки**: по 8.5 это тоже «запас пуст», значит, без станции жильцы обезвоживаются.
    Поэтому жизнеобеспечение лучше включать после того, как игроки успеют поставить ФВУ и станцию (см. ниже).
16. Сохранения v2 переносятся автоматически: ядро получает область 3×3×3 вокруг себя, комнаты — свои кубы 5×5×5,
    все включены, воздух 100%, хранилища пусты.

**Выкатка:** первые 1–2 сессии держать `bunker.lifeSupport.enabled = false` в `bunker-server.toml`, потом включить.

## Что настроить в сборке

* **Nuclear Radiation** — от него ничего в сборке не зависит; по разделу 1.4 его лучше убрать, иначе игрок получает дозу от двух систем.
* **Wastelands** — ничего менять не нужно, пока `radiation.wastelandsOverride = true`.
* **Weather2** — блоки мода защищены миксином; дополнительно можно включить `blockBreakingInvokesCancellableEvent` в его конфиге.
* **Better Weather** — множитель кислотного дождя: `radiation.weather.acidRainMultiplier` (по умолчанию 1.0, то есть выключен).

## Текстуры и звуки

Все текстуры и звуки — заглушки (`assets/bunker/textures`, `assets/bunker/sounds`). Броня: `textures/models/armor/radsuit_layer_{1,2}.png`
(основа) и `..._overlay.png` (белый слой, тонируется цветом уровня). Значок уровня на иконке — `item/radsuit_badge.png`.

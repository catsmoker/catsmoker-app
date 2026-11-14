package com.catsmoker.app.screens.editgamefiles

import com.catsmoker.app.screens.editgamefiles.games.alienisolation.alienIsolationConfig
import com.catsmoker.app.screens.editgamefiles.games.civilization.civilizationConfig
import com.catsmoker.app.screens.editgamefiles.games.companyofheroes.companyOfHeroesConfig
import com.catsmoker.app.screens.editgamefiles.games.empire.empireConfig
import com.catsmoker.app.screens.editgamefiles.games.gridlegends.gridLegendsConfig
import com.catsmoker.app.screens.editgamefiles.games.hitmanabsolution.hitmanAbsolutionConfig
import com.catsmoker.app.screens.editgamefiles.games.hitmanbloodmoney.hitmanBloodMoneyConfig
import com.catsmoker.app.screens.editgamefiles.games.laracroftgol.laraCroftGolConfig
import com.catsmoker.app.screens.editgamefiles.games.laracrofttoo.laraCroftTooConfig
import com.catsmoker.app.screens.editgamefiles.games.medieval.medievalConfig
import com.catsmoker.app.screens.editgamefiles.games.minecraft.minecraftConfig
import com.catsmoker.app.screens.editgamefiles.games.napoleon.napoleonConfig
import com.catsmoker.app.screens.editgamefiles.games.railroads.railroadsConfig
import com.catsmoker.app.screens.editgamefiles.games.rome.romeConfig
import com.catsmoker.app.screens.editgamefiles.games.romealexander.romeAlexanderConfig
import com.catsmoker.app.screens.editgamefiles.games.romebarbarian.romeBarbarianConfig
import com.catsmoker.app.screens.editgamefiles.games.saintsrow.saintsRowConfig
import com.catsmoker.app.screens.editgamefiles.games.terraria.terrariaConfig
import com.catsmoker.app.screens.editgamefiles.games.tombraider.tombRaiderConfig
import com.catsmoker.app.screens.editgamefiles.games.tropico.tropicoConfig
import com.catsmoker.app.screens.editgamefiles.games.xcom.xcomConfig

/** Wiring only. Each game's file schema and evidence stay with that game's owner. */
val additionalGameEditors = listOf(
    gridLegendsConfig, alienIsolationConfig, saintsRowConfig, napoleonConfig, hitmanBloodMoneyConfig,
    laraCroftGolConfig, laraCroftTooConfig, railroadsConfig, tombRaiderConfig, hitmanAbsolutionConfig,
    companyOfHeroesConfig, empireConfig, tropicoConfig, romeConfig, medievalConfig,
    romeBarbarianConfig, romeAlexanderConfig, xcomConfig, minecraftConfig, terrariaConfig, civilizationConfig
).associateBy { it.game }

package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;

/**
 * Те же требования к restore_checkpoint, что в {@link RestoreCheckpointLevelsTest}, но дом ведёт
 * себя как в работающем приложении: каждый добавленный уровень сразу становится выбранным.
 * Команда, которая раскладывает объекты по выбранному уровню, в таком доме собирает их все на
 * уровне, добавленном последним, — на голой модели этого не видно.
 */
class RestoreCheckpointLevelsInApplicationTest extends RestoreCheckpointLevelsTest {

    @Override
    Home newHome() {
        return LevelSceneFixture.applicationHome();
    }
}

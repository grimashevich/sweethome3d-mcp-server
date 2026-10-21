package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.UserPreferences;

import java.util.Locale;

/**
 * Настоящие настройки SH3D ({@link DefaultUserPreferences}) без побочного действия на локаль JVM.
 *
 * <p>{@code UserPreferences} SH3D 7.5 при создании ставит локаль JVM по умолчанию по своему языку
 * интерфейса ({@code Locale.setDefault}); языка {@code tr} или {@code ar} у неё нет, и локаль
 * {@code tr_TR} становится {@code en_TR}. В тестах это незаметно переключило бы локаль для всех
 * следующих тестов в той же JVM — и прогон набора в {@code tr_TR} перестал бы проверять {@code tr_TR}.
 * Здесь локаль после создания настроек возвращается прежней.
 */
final class Sh3dPreferences {

    private Sh3dPreferences() {
    }

    static UserPreferences create() {
        Locale all = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            return new DefaultUserPreferences();
        } finally {
            Locale.setDefault(all);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }
}

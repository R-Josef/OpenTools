package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TextContextMenusTest {
    private final LanguageManager language = new LanguageManager(
            Path.of("target", "test-language"),
            "en_US"
    );

    @Test
    void editableTextGetsEditingActions() {
        JTextField field = new JTextField();
        TextContextMenus.install(field, language);

        assertEquals(Arrays.asList("Cut", "Copy", "Paste", null, "Select all"), labels(field));
    }

    @Test
    void readOnlyTextOnlyGetsCopyActions() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        TextContextMenus.install(area, language);

        assertEquals(Arrays.asList("Copy", null, "Select all"), labels(area));
    }

    private static List<String> labels(javax.swing.text.JTextComponent text) {
        JPopupMenu menu = text.getComponentPopupMenu();
        return Arrays.stream(menu.getComponents())
                .map(TextContextMenusTest::label)
                .toList();
    }

    private static String label(Component component) {
        return component instanceof JMenuItem item ? item.getText() : null;
    }
}

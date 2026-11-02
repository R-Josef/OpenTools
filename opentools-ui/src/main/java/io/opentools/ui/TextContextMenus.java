package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;

final class TextContextMenus {
    private TextContextMenus() {
    }

    static void install(Component root, LanguageManager language) {
        if (root instanceof JTextComponent text) {
            install(text, language);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                install(child, language);
            }
        }
    }

    private static void install(JTextComponent text, LanguageManager language) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem cut = item(language, "text.cut", "Cut", text::cut);
        JMenuItem copy = item(language, "text.copy", "Copy", text::copy);
        JMenuItem paste = item(language, "text.paste", "Paste", text::paste);
        JMenuItem selectAll = item(language, "text.select_all", "Select all", text::selectAll);
        if (text.isEditable()) {
            menu.add(cut);
            menu.add(copy);
            menu.add(paste);
        } else {
            menu.add(copy);
        }
        menu.addSeparator();
        menu.add(selectAll);
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                boolean hasSelection = text.getSelectionStart() != text.getSelectionEnd();
                cut.setEnabled(text.isEditable() && hasSelection);
                copy.setEnabled(hasSelection);
                paste.setEnabled(text.isEditable());
                selectAll.setEnabled(text.getDocument().getLength() > 0);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
            }
        });
        text.setComponentPopupMenu(menu);
    }

    private static JMenuItem item(
            LanguageManager language,
            String key,
            String fallback,
            Runnable action
    ) {
        JMenuItem item = new JMenuItem(language.text(key, fallback));
        item.addActionListener(ignored -> action.run());
        return item;
    }
}

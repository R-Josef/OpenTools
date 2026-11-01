package io.opentools.ui;

import javax.accessibility.AccessibleContext;
import java.awt.Component;
import java.awt.Container;

final class UiAccessibility {
    private UiAccessibility() {
    }

    static void name(Component component, String name) {
        if (component == null || name == null || name.isBlank()) {
            return;
        }
        AccessibleContext context = component.getAccessibleContext();
        if (context != null) {
            context.setAccessibleName(name);
        }
    }

    static void describe(Component component, String description) {
        if (component == null || description == null || description.isBlank()) {
            return;
        }
        AccessibleContext context = component.getAccessibleContext();
        if (context != null) {
            context.setAccessibleDescription(description);
        }
    }

    static void nameTree(Component component, String name, String description) {
        name(component, name);
        describe(component, description);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                name(child, name);
                describe(child, description);
            }
        }
    }
}

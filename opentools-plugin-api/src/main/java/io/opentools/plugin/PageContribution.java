package io.opentools.plugin;

import javax.swing.JPanel;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A page contribution registered by a plugin.
 *
 * @param id host-qualified page identifier
 * @param title display title
 * @param panelFactory factory for the page component
 */
public record PageContribution(String id, String title, Supplier<JPanel> panelFactory) {
    public PageContribution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(panelFactory, "panelFactory");
    }
}

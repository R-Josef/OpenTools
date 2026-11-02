package io.opentools.plugin;

import javax.swing.JPanel;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A page contribution registered by a plugin.
 *
 * A plugin page whose title follows the active interface language.
 */
public final class PageContribution {
    private final String id;
    private final String titleKey;
    private final String fallbackTitle;
    private final PluginLocalizer localizer;
    private final Supplier<JPanel> panelFactory;

    /**
     * Creates a localized page contribution.
     *
     * @param id host-qualified page identifier
     * @param titleKey page title resource key
     * @param fallbackTitle fallback page title
     * @param localizer owning plugin localizer
     * @param panelFactory factory for the page component
     */
    public PageContribution(
            String id,
            String titleKey,
            String fallbackTitle,
            PluginLocalizer localizer,
            Supplier<JPanel> panelFactory
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.titleKey = Objects.requireNonNull(titleKey, "titleKey");
        this.fallbackTitle = Objects.requireNonNull(fallbackTitle, "fallbackTitle");
        this.localizer = Objects.requireNonNull(localizer, "localizer");
        this.panelFactory = Objects.requireNonNull(panelFactory, "panelFactory");
    }

    /**
     * Returns the host-qualified page identifier.
     *
     * @return page identifier
     */
    public String id() {
        return id;
    }

    /**
     * Resolves the page title in the active language.
     *
     * @return localized page title
     */
    public String title() {
        return localizer.text(titleKey, fallbackTitle);
    }

    /**
     * Returns the page component factory.
     *
     * @return panel factory
     */
    public Supplier<JPanel> panelFactory() {
        return panelFactory;
    }
}

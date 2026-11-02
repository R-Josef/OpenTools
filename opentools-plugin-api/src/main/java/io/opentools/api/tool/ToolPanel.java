package io.opentools.api.tool;

import javax.swing.JPanel;
import java.awt.LayoutManager;

/**
 * Swing page owned by an interactive tool.
 *
 * <p>The host closes the panel when the user selects another tool, changes
 * project, or unloads the owning plugin. Subclasses that use timers or external
 * listeners should override {@link #close()}.</p>
 */
public abstract class ToolPanel extends JPanel implements AutoCloseable {
    protected ToolPanel() {
    }

    protected ToolPanel(LayoutManager layout) {
        super(layout);
    }

    @Override
    public void close() {
    }
}

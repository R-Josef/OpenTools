package io.opentools.ui;

import io.opentools.api.tool.ToolRequest;

@FunctionalInterface
interface ToolRunSubmitter {
    String submit(ToolRequest request) throws Exception;
}

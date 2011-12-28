package org.petero.droidfish.engine;

import java.util.HashMap;

public abstract class UCIEngineBase implements UCIEngine {

    private boolean processAlive;

    protected UCIEngineBase() {
        processAlive = false;
    }

    protected abstract void startProcess();

    @Override
    public final void initialize() {
        if (!processAlive) {
            startProcess();
            processAlive = true;
        }
    }

    @Override
    public final void shutDown() {
        if (processAlive) {
            writeLineToEngine("quit");
            processAlive = false;
        }
    }

    @Override
    public void setOption(String name, int value) {
        setOption(name, String.format("%d", value));
    }

    @Override
    public void setOption(String name, boolean value) {
        setOption(name, value ? "true" : "false");
    }

    private HashMap<String, String> options = new HashMap<String, String>();

    @Override
    public void setOption(String name, String value) {
        String currVal = options.get(name.toLowerCase());
        if (value.equals(currVal))
            return;
        writeLineToEngine(String.format("setoption name %s value %s", name, value));
        options.put(name.toLowerCase(), value);
    }
}

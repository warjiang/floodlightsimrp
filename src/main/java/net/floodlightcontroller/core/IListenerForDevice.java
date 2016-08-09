package net.floodlightcontroller.core;

public interface IListenerForDevice <Sting> {
    public enum Command {
        CONTINUE, STOP
    }
    
    /**
     * The name assigned to this listener
     * @return
     */
    public String getName();

    /**
     * Check if the module called name is a callback ordering prerequisite
     * for this module.  In other words, if this function returns true for 
     * the given name, then this listener will be called after that
     * message listener.
     * @param type the object type to which this applies
     * @param name the name of the module
     * @return whether name is a prerequisite.
     */
    public boolean isCallbackOrderingPrereqForDevice(Sting type, String name);

    /**
     * Check if the module called name is a callback ordering post-requisite
     * for this module.  In other words, if this function returns true for 
     * the given name, then this listener will be called before that
     * message listener.
     * @param type the object type to which this applies
     * @param name the name of the module
     * @return whether name is a post-requisite.
     */
    public boolean isCallbackOrderingPostreqForDevice(Sting type, String name);
}

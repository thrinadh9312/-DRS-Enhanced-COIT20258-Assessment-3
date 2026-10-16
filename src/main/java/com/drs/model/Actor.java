package com.drs.model;
/** Immutable authenticated principal. Never accepts a role claimed by a client. */
public record Actor(long id,String username,String role,String department) {
    public boolean coordinator(){return role.equals("ADMIN")||role.equals("COORDINATOR");}
    @Override public String toString(){return username+" ("+role+")";}
}

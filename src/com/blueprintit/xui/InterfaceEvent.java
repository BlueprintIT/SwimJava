/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.xui;

public class InterfaceEvent
{
	public static int INTERFACE_CREATED = 0;
	
	private UserInterface iface;
	private int type;
	
	public InterfaceEvent(UserInterface source, int type)
	{
		this.iface=source;
		this.type=type;
	}
	
	public UserInterface getUserInterface()
	{
		return iface;
	}
	
	public int getType()
	{
		return type;
	}
}

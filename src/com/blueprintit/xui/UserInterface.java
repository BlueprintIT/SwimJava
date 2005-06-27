/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.xui;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.Window;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.swixml.SwingEngine;

public class UserInterface extends SwingEngine
{
	private Logger log = Logger.getLogger(this.getClass());

	private Container container = null;
	private String resource;
	private List listeners = new LinkedList();
	
	public UserInterface(String resource) throws Exception
	{
		super();
		this.resource=resource;
		buildUI();
	}
	
	public UserInterface(Object obj) throws Exception
	{
		super(obj);
		resource = obj.getClass().getName();
		if (obj instanceof InterfaceListener)
		{
			addInterfaceListener((InterfaceListener)obj);
		}
		buildUI();
	}
	
	public UserInterface(String resource, Container container) throws Exception
	{
		super();
		this.resource=resource;
		this.container=container;
		buildUI();
	}
	
	public UserInterface(Object obj, Container container) throws Exception
	{
		super(obj);
		this.resource=obj.getClass().getName();
		this.container=container;
		if (obj instanceof InterfaceListener)
		{
			addInterfaceListener((InterfaceListener)obj);
		}
		buildUI();
	}
	
	public void addInterfaceListener(InterfaceListener listener)
	{
		listeners.add(listener);
	}
	
	public void removeInterfaceListener(InterfaceListener listener)
	{
		listeners.remove(listener);
	}
	
	public Container getContainer()
	{
		return container;
	}
	
	public void show()
	{
		if (container instanceof Dialog)
		{
			((Dialog)container).setModal(false);
		}
		if (container instanceof Window)
		{
			((Window)container).show();
		}
		else
		{
			log.warn("Attempt to show something that isn't a window: "+container.getClass());
		}
	}
	
	public void showModal()
	{
		if (container instanceof Dialog)
		{
			Dialog dialog = (Dialog)container;
			dialog.setModal(true);
			dialog.show();
		}
		else
		{
			log.warn("Attempt to show modal something that isn't a dialog: "+container.getClass());
		}
	}
	
	private Reader getXUIReader()
	{
		return new InputStreamReader(getClient().getClass().getClassLoader().getResourceAsStream(resource.replace('.','/')+".xml"));
	}
	
	private void fireInterfaceCreated()
	{
		InterfaceEvent ev = new InterfaceEvent(this,InterfaceEvent.INTERFACE_CREATED);
		Iterator i = listeners.iterator();
		while (i.hasNext())
		{
			try
			{
				InterfaceListener l = (InterfaceListener)i.next();
				l.interfaceCreated(ev);
			}
			catch (Throwable t)
			{
				log.warn("Listener threw something",t);
			}
		}
	}
	
	private void buildUI() throws Exception
	{
		if (container==null)
		{
			container=this.render(getXUIReader());
		}
		else
		{
			// TODO throws an exception with the latest SwiXML.
			/*
			 * java.security.AccessControlException: access denied (java.lang.reflect.ReflectPermission suppressAccessChecks)
			 *	at java.security.AccessControlContext.checkPermission(Unknown Source)
			 *	at java.security.AccessController.checkPermission(Unknown Source)
			 *	at java.lang.SecurityManager.checkPermission(Unknown Source)
			 *	at java.lang.reflect.AccessibleObject.setAccessible(Unknown Source)
			 *	at org.swixml.SwingEngine.mapMembers(Unknown Source)
			 *	at org.swixml.SwingEngine.mapMembers(Unknown Source)
			 *	at org.swixml.SwingEngine.insert(Unknown Source)
			 *	at org.swixml.SwingEngine.insert(Unknown Source)
			 *	at com.blueprintit.xui.UserInterface.buildUI(UserInterface.java:111)
			 *	at com.blueprintit.xui.UserInterface.<init>(UserInterface.java:62)
			 *	at com.blueprintit.webedit.WebEdit.init(WebEdit.java:44)
			 *	at sun.applet.AppletPanel.run(Unknown Source)
			 *	at java.lang.Thread.run(Unknown Source)
			 */
			this.insert(getXUIReader(),container);
		}
		fireInterfaceCreated();
	}
}

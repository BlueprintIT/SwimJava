/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.swim;

import java.io.BufferedReader;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.HttpURLConnection;

import org.apache.log4j.Logger;

public class SwimInterface
{
	private Logger log = Logger.getLogger(this.getClass());

	private class ResourceOutputStream extends OutputStream
	{
		private OutputStream out;
		private HttpURLConnection connection;

		public ResourceOutputStream(HttpURLConnection connection) throws IOException
		{
			this.connection=connection;
			connection.setDoOutput(true);
			this.out=connection.getOutputStream();
		}

		public void write(int b) throws IOException
		{
			out.write(b);
		}
		
		public void close() throws IOException
		{
			out.close();
			connection.connect();
			connection.getInputStream().close();
		}
	}
	
	private URL url;
	
	public SwimInterface(URL url)
	{
		this.url=url;
	}
	
	public URL getURL()
	{
		return url;
	}
	
	public String getResource(String path, String version) throws IOException
	{
		BufferedReader reader = new BufferedReader(openResourceReader(path,version));
		StringBuffer result = new StringBuffer();
		String line=reader.readLine();
		while (line!=null)
		{
			result.append(line);
			line=reader.readLine();
		}
		reader.close();
		return result.toString();
	}
	
	public void setResource(String path, String version, String data) throws IOException
	{
		Writer writer = openResourceWriter(path,version);
		writer.write(data);
		writer.close();
	}
	
	public Reader openResourceReader(String path, String version) throws IOException
	{
		return new InputStreamReader(openResourceInputStream(path,version));
	}
	
	public Writer openResourceWriter(String path, String version) throws IOException
	{
		return new OutputStreamWriter(openResourceOutputStream(path,version));
	}
	
	public InputStream openResourceInputStream(String path, String version) throws IOException
	{
		Request request = new Request(this,"view",path);
		if (version!=null)
		{
			request.addParameter("version",version);
		}
		return openConnection("GET",request).getInputStream();
	}
	
	public OutputStream openResourceOutputStream(String path, String version) throws IOException
	{
		Request request = new Request(this,"view",path);
		if (version!=null)
		{
			request.addParameter("version",version);
		}
		HttpURLConnection connection = openConnection("PUT",request);
		return new ResourceOutputStream(connection);
	}
	
	public HttpURLConnection openConnection(String method, Request request) throws IOException
	{
		URL url = request.encode();
		log.info("Opening connection to "+url.toString());
		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
		connection.setRequestMethod(method);
		return connection;
	}
}

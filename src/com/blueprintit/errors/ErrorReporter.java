package com.blueprintit.errors;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;

public class ErrorReporter extends StringWriter
{
	private String title;
	private String text;
	
	private ErrorReporter(String title, String text)
	{
		super();
		this.title=title;
		this.text=text;
	}
	
	public void close()
	{
		try
		{
			URL url = new URL("http://www.blueprintit.co.uk/reporter/error.php");
			String report = getBuffer().toString();
			
			if (QueryReportDialog.querySendReport(title,text,report))
			{
				try
				{
					HttpURLConnection connection = (HttpURLConnection)url.openConnection();
					connection.setRequestMethod("PUT");
					connection.setDoOutput(true);
					Writer out = new OutputStreamWriter(connection.getOutputStream());
					out.write(getBuffer().toString());
					out.close();
					connection.connect();
					connection.getInputStream().close();
				}
				catch (Exception e)
				{
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static PrintWriter openErrorReport(String title, String text, String product, String component)
	{
		PrintWriter out = new PrintWriter(new ErrorReporter(title,text));
		out.println("Product: "+product);
		out.println("Component: "+component);
		return out;
	}
	
	public static void sendErrorReport(String title, String text, String product, String component, String message, Throwable error)
	{
		PrintWriter er = openErrorReport(title,text,product,component);
		er.println(message);
		error.printStackTrace(er);
		er.close();
	}
}

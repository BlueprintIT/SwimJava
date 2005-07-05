package com.blueprintit.errors;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

public class QueryReportDialog extends JDialog
{
	private boolean result;
	private String title;
	private String text;
	private String report;
	
	private class ReportDisplay extends JDialog
	{
		public ReportDisplay()
		{
			initDialog();
			show();
		}
		
		private void initDialog()
		{
			setTitle("Error Report");
			int width=300;
			int height=400;
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
	    int x = (screen.width - width) / 2;
	    int y = (screen.height - height) / 2;
	    setBounds(x, y, width, height);
	    getContentPane().setLayout(new BorderLayout());
	    setModal(true);
			
			JTextArea text = new JTextArea();
			text.setEditable(false);
			text.setWrapStyleWord(true);
			text.setLineWrap(true);
			text.setText(report);
			text.setBackground(new Color(0,0,0,0));

			JScrollPane scroll = new JScrollPane(text);
			scroll.setBorder(new CompoundBorder(new EmptyBorder(5,5,5,5),new BevelBorder(BevelBorder.LOWERED)));
			
			getContentPane().add(scroll);
			
			JPanel panel = new JPanel();
			getContentPane().add(panel,BorderLayout.SOUTH);
			
			JButton button = new JButton();
			button.setText("OK");
			button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e)
				{
					hide();
				}
			});
			panel.add(button);
		}
	}
	
	private QueryReportDialog(String title, String text, String report)
	{
		super();
		this.title=title;
		this.text=text;
		this.report=report;
		result=false;
		initDialog();
		setModal(true);
	}
	
	private void initDialog()
	{
		GridBagLayout layout = new GridBagLayout();
		
		int width=300;
		int height=200;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    int x = (screen.width - width) / 2;
    int y = (screen.height - height) / 2;
    setBounds(x, y, width, height);
		setTitle(title);
		setResizable(false);
		getContentPane().setLayout(layout);
		
		GridBagConstraints gc = new GridBagConstraints();
		
		gc.fill=GridBagConstraints.BOTH;
		gc.insets = new Insets(5,5,5,5);
		gc.weightx=1;
		gc.weighty=1;
		gc.gridwidth=3;
		gc.gridx=0;
		gc.gridy=0;
		JLabel temp = new JLabel();
		
		JTextArea label = new JTextArea();
		label.setFont(temp.getFont());
		label.setBackground(new Color(0,0,0,0));
		label.setEditable(false);
		label.setWrapStyleWord(true);
		label.setLineWrap(true);
		label.setText(text);
		layout.setConstraints(label,gc);
		getContentPane().add(label);

		gc.gridy=1;
		label = new JTextArea();
		label.setFont(temp.getFont());
		label.setBackground(new Color(0,0,0,0));
		label.setEditable(false);
		label.setWrapStyleWord(true);
		label.setLineWrap(true);
		label.setText("Would you like to try to send an error report to Blueprint IT Ltd.? The report can only be sent if you have an internet connection. "+
				"To see what the report contains, please click \"More Details\".");
		layout.setConstraints(label,gc);
		getContentPane().add(label);

		gc.fill=GridBagConstraints.HORIZONTAL;
		gc.gridy=2;
		gc.gridwidth=1;
		JButton button = new JButton();
		button.setText("Yes");
		layout.setConstraints(button,gc);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				result=true;
				hide();
			}
		});
		getContentPane().add(button);

		gc.gridx=1;
		button = new JButton();
		button.setText("No");
		layout.setConstraints(button,gc);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				result=false;
				hide();
			}
		});
		getContentPane().add(button);

		gc.gridx=2;
		button = new JButton();
		button.setText("More Details...");
		layout.setConstraints(button,gc);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				new ReportDisplay();
			}
		});
		getContentPane().add(button);
	}
	
	private boolean getResult()
	{
		return result;
	}
	
	public static boolean querySendReport(String title, String text, String report)
	{
		QueryReportDialog dialog = new QueryReportDialog(title,text,report);
		dialog.show();
		return dialog.getResult();
	}
}

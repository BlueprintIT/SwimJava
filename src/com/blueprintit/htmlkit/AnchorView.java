package com.blueprintit.htmlkit;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.Position;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.CSS;
import javax.swing.text.html.ImageView;

import org.apache.log4j.Logger;

public class AnchorView extends View
{
	private Logger log = Logger.getLogger(this.getClass());

	private ImageView image;
  private static Icon anchorIcon;

  /**
	 * Creates a new view that represents an IMG element.
	 * 
	 * @param elem
	 *          the element to create a view for
	 */
	public AnchorView(ImageView image)
	{
		super(image.getElement());
		this.image=image;
		if (anchorIcon==null)
			anchorIcon = new ImageIcon(this.getClass().getClassLoader().getResource("com/blueprintit/htmlkit/icons/anchor.gif"));
	}

	public boolean isDisplayingImage()
	{
		AttributeSet attrs = image.getAttributes();
		return !attrs.isDefined(CSS.Attribute.FLOAT);
	}
	
	public boolean isDisplayingAnchor()
	{
		return !isDisplayingImage();
	}
	
	public ImageView getImageView()
	{
		return image;
	}
	
  public AttributeSet getAttributes()
  {
  	if (isDisplayingImage())
  	{
  		return image.getAttributes();
  	}
  	else
  	{
  		return super.getAttributes();
  	}
  }

  public String getToolTipText(float x, float y, Shape allocation)
  {
  	if (isDisplayingImage())
  	{
  		return image.getToolTipText(x,y,allocation);
  	}
  	else
  	{
  		return "Image Anchor";
  	}
  }
  
  public void setParent(View parent)
  {
  	image.setParent(parent);
  	super.setParent(parent);
  }
  
  public void changedUpdate(DocumentEvent e, Shape a, ViewFactory f)
  {
  	super.changedUpdate(e,a,f);
  	image.changedUpdate(e,a,f);
  }
  
	public void paint(Graphics g, Shape a)
	{
		if (isDisplayingImage())
		{
			image.paint(g, a);
		}
		else if (isDisplayingAnchor())
		{
			int start = getStartOffset();
			int end = getEndOffset();
			Component c = getContainer();
			if (c instanceof JTextComponent)
			{
				JTextComponent tc = (JTextComponent) c;
				Highlighter h = tc.getHighlighter();
				if (h instanceof LayeredHighlighter)
				{
					((LayeredHighlighter) h).paintLayeredHighlights(g, start, end, a, tc, this);
				}
			}
			Rectangle rect = (a instanceof Rectangle) ? (Rectangle) a : a.getBounds();
			anchorIcon.paintIcon(getContainer(), g, rect.x, rect.y);
		}
	}

	public float getPreferredSpan(int axis)
	{
		if (isDisplayingImage())
		{
			return image.getPreferredSpan(axis);
		}
		else if (isDisplayingAnchor())
		{
			switch (axis)
			{
				case View.X_AXIS:
					return anchorIcon.getIconWidth();
				case View.Y_AXIS:
					return anchorIcon.getIconHeight();
				default:
					throw new IllegalArgumentException("Invalid axis: "+axis);
			}
		}
		else
		{
			return 0;
		}
	}

  public float getAlignment(int axis)
  {
  	if (isDisplayingImage())
  	{
  		return image.getAlignment(axis);
  	}
  	else
  	{
  		return 1f;
  	}
  }
  
	public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException
	{
		if (isDisplayingImage())
		{
			return image.modelToView(pos,a,b);
		}
		else
		{
			int p0 = getStartOffset();
			int p1 = getEndOffset();
			if ((pos>=p0)&&(pos<=p1))
			{
				Rectangle r = a.getBounds();
				if (pos==p1)
				{
					r.x += r.width;
				}
				r.width = 0;
				return r;
			}
			return null;
		}
	}

	public int viewToModel(float x, float y, Shape a, Position.Bias[] bias)
	{
		if (isDisplayingImage())
		{
			return image.viewToModel(x,y,a,bias);
		}
		else
		{
			Rectangle alloc = (Rectangle) a;
			if (x<alloc.getX()+alloc.getWidth()/2)
			{
				bias[0] = Position.Bias.Forward;
				return getStartOffset();
			}
			bias[0] = Position.Bias.Backward;
			return getEndOffset();
		}
	}

	public void setSize(float width, float height)
	{
		if (isDisplayingImage())
		{
			image.setSize(width,height);
		}
		else
		{
			super.setSize(width,height);
		}
	}
}

package com.blueprintit.htmlkit;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.View;

public class AnchorView extends View
{
  private static Icon anchorIcon;

  /**
	 * Creates a new view that represents an IMG element.
	 * 
	 * @param elem
	 *          the element to create a view for
	 */
	public AnchorView(Element elem)
	{
		super(elem);
		if (anchorIcon==null)
			anchorIcon = new ImageIcon(this.getClass().getClassLoader().getResource("com/blueprintit/htmlkit/icons/anchor.gif"));
	}

  /**
	 * Paints the View.
	 * 
	 * @param g
	 *          the rendering surface to use
	 * @param a
	 *          the allocated region to render into
	 * @see View#paint
	 */
	public void paint(Graphics g, Shape a)
	{
		Rectangle rect = (a instanceof Rectangle) ? (Rectangle) a : a.getBounds();
		anchorIcon.paintIcon(getContainer(), g, rect.x, rect.y);
	}

  /**
	 * Determines the preferred span for this view along an axis.
	 * 
	 * @param axis
	 *          may be either X_AXIS or Y_AXIS
	 * @return the span the view would like to be rendered into; typically the
	 *         view is told to render into the span that is returned, although
	 *         there is no guarantee; the parent may choose to resize or break the
	 *         view
	 */
	public float getPreferredSpan(int axis)
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

  /**
	 * Provides a mapping from the document model coordinate space to the
	 * coordinate space of the view mapped to it.
	 * 
	 * @param pos
	 *          the position to convert
	 * @param a
	 *          the allocated region to render into
	 * @return the bounding box of the given position
	 * @exception BadLocationException
	 *              if the given position does not represent a valid location in
	 *              the associated document
	 * @see View#modelToView
	 */
	public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException
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

  /**
	 * Provides a mapping from the view coordinate space to the logical coordinate
	 * space of the model.
	 * 
	 * @param x
	 *          the X coordinate
	 * @param y
	 *          the Y coordinate
	 * @param a
	 *          the allocated region to render into
	 * @return the location within the model that best represents the given point
	 *         of view
	 * @see View#viewToModel
	 */
	public int viewToModel(float x, float y, Shape a, Position.Bias[] bias)
	{
		Rectangle alloc = (Rectangle) a;
		if (x<alloc.x+alloc.width)
		{
			bias[0] = Position.Bias.Forward;
			return getStartOffset();
		}
		bias[0] = Position.Bias.Backward;
		return getEndOffset();
	}
}

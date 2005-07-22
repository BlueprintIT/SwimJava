/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.htmlkit;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.SizeRequirements;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Position;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.Element;
import javax.swing.text.FlowView;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTML;
import javax.swing.text.html.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

import org.apache.log4j.Logger;

public class WebEditParagraphView extends ParagraphView
{
	private Logger log = Logger.getLogger(this.getClass());

	static class AdvancedFlowStrategy extends FlowStrategy
	{
		private Logger log = Logger.getLogger(this.getClass());

		public void layout(FlowView fv)
		{
			log.info("layout");
			((WebEditParagraphView)fv).invalidateImages();
			super.layout(fv);
			((WebEditParagraphView)fv).layoutImages(fv.getViewCount()-1);
			AttributeSet attr = fv.getAttributes();
			float lineSpacing = StyleConstants.getLineSpacing(attr);
			boolean justifiedAlignment = (StyleConstants.getAlignment(attr) == StyleConstants.ALIGN_JUSTIFIED);
			if (!(justifiedAlignment || (lineSpacing > 1)))
			{
				return;
			}

			int cnt = fv.getViewCount();
			for (int i = 0; i < cnt - 1; i++)
			{
				AdvancedRow row = (AdvancedRow) fv.getView(i);
				/*if (lineSpacing > 1)
				{
					float height = row.getMinimumSpan(View.Y_AXIS);
					float addition = (height * lineSpacing) - height;
					if (addition > 0)
					{
						row.setInsets(row.getTopInset(), row.getLeftInset(),
								(short) addition, row.getRightInset());
					}
				}*/

				if (justifiedAlignment)
				{
					restructureRow(row, i);
					row.setRowNumber(i + 1);
				}
			}
		}

		protected int layoutRow(FlowView fv, int rowIndex, int pos)
		{
			if (rowIndex>0)
				((WebEditParagraphView)fv).layoutImages(rowIndex-1);
			return super.layoutRow(fv, rowIndex, pos);
		}
		
		protected void restructureRow(View row, int rowNum)
		{
			int rowStartOffset = row.getStartOffset();
			int rowEndOffset = row.getEndOffset();
			String rowContent = "";
			try
			{
				rowContent = row.getDocument().getText(rowStartOffset,
						rowEndOffset - rowStartOffset);
				if (rowNum == 0)
				{
					while (rowContent.charAt(0) == ' ')
					{
						rowContent = rowContent.substring(1);
						if (rowContent.length() == 0)
							break;
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			int rowSpaceCount = getSpaceCount(rowContent);
			if (rowSpaceCount < 1)
				return;
			int[] rowSpaceIndexes = getSpaceIndexes(rowContent, row.getStartOffset());
			int currentSpaceIndex = 0;

			for (int i = 0; i < row.getViewCount(); i++)
			{
				View child = row.getView(i);
				if ((child.getStartOffset() < rowSpaceIndexes[currentSpaceIndex])
						&& (child.getEndOffset() > rowSpaceIndexes[currentSpaceIndex]))
				{
					//split view
					View first = child.createFragment(child.getStartOffset(),
							rowSpaceIndexes[currentSpaceIndex]);
					View second = child.createFragment(
							rowSpaceIndexes[currentSpaceIndex], child.getEndOffset());
					View[] repl = new View[2];
					repl[0] = first;
					repl[1] = second;

					row.replace(i, 1, repl);
					currentSpaceIndex++;
					if (currentSpaceIndex >= rowSpaceIndexes.length)
						break;
				}
			}
		}

	}

	class AdvancedRow extends BoxView
	{
		private int rowNumber = 0;

		AdvancedRow(Element elem)
		{
			super(elem, View.X_AXIS);
		}

		protected void loadChildren(ViewFactory f)
		{
		}

		public AttributeSet getAttributes()
		{
			View p = getParent();
			return (p != null) ? p.getAttributes() : null;
		}

		public float getAlignment(int axis)
		{
			if (axis == View.X_AXIS)
			{
				AttributeSet attr = getAttributes();
				int justification = StyleConstants.getAlignment(attr);
				switch (justification)
				{
					case StyleConstants.ALIGN_LEFT:
					case StyleConstants.ALIGN_JUSTIFIED:
						return 0;
					case StyleConstants.ALIGN_RIGHT:
						return 1;
					case StyleConstants.ALIGN_CENTER:
						return 0.5f;
				}
			}
			return super.getAlignment(axis);
		}

		public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException
		{
			Rectangle r = a.getBounds();
			View v = getViewAtPosition(pos, r);
			if ((v != null) && (!v.getElement().isLeaf()))
			{
				// Don't adjust the height if the view represents a branch.
				return super.modelToView(pos, a, b);
			}
			r = a.getBounds();
			int height = r.height;
			int y = r.y;
			Shape loc = super.modelToView(pos, a, b);
			r = loc.getBounds();
			r.height = height;
			r.y = y;
			return r;
		}

		public int getStartOffset()
		{
			int offs = Integer.MAX_VALUE;
			int n = getViewCount();
			for (int i = 0; i < n; i++)
			{
				View v = getView(i);
				offs = Math.min(offs, v.getStartOffset());
			}
			return offs;
		}

		public int getEndOffset()
		{
			int offs = 0;
			int n = getViewCount();
			for (int i = 0; i < n; i++)
			{
				View v = getView(i);
				offs = Math.max(offs, v.getEndOffset());
			}
			return offs;
		}

		protected void layoutMinorAxis(int targetSpan, int axis, int[] offsets, int[] spans)
		{
			baselineLayout(targetSpan, axis, offsets, spans);
		}

		protected SizeRequirements calculateMinorAxisRequirements(int axis, SizeRequirements r)
		{
			return baselineRequirements(axis, r);
		}

		protected int getViewIndexAtPosition(int pos)
		{
			// This is expensive, but are views are not necessarily layed
			// out in model order.
			if (pos < getStartOffset() || pos >= getEndOffset())
				return -1;
			for (int counter = getViewCount() - 1; counter >= 0; counter--)
			{
				View v = getView(counter);
				if (pos >= v.getStartOffset() && pos < v.getEndOffset())
				{
					return counter;
				}
			}
			return -1;
		}

		public short getTopInset()
		{
			return super.getTopInset();
		}

		public short getLeftInset()
		{
			return super.getLeftInset();
		}

		public short getRightInset()
		{
			return super.getRightInset();
		}

		public void setInsets(short topInset, short leftInset, short bottomInset, short rightInset)
		{
			super.setInsets(topInset, leftInset, bottomInset, rightInset);
		}

		protected void layoutMajorAxis(int targetSpan, int axis, int[] offsets, int[] spans)
		{
			super.layoutMajorAxis(targetSpan, axis, offsets, spans);
			AttributeSet attr = getAttributes();
			if ((StyleConstants.getAlignment(attr) != StyleConstants.ALIGN_JUSTIFIED)
					&& (axis != View.X_AXIS))
			{
				return;
			}
			int cnt = offsets.length;

			int span = 0;
			for (int i = 0; i < cnt; i++)
			{
				span += spans[i];
			}
			if (getRowNumber() == 0)
				return;
			int startOffset = getStartOffset();
			int len = getEndOffset() - startOffset;
			String context = "";
			try
			{
				context = getElement().getDocument().getText(startOffset, len);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			int spaceCount = getSpaceCount(context);
			if (context.charAt(context.length()-1)==' ')
				spaceCount--;

			int pixelsToAdd = targetSpan - span;

			if (this.getRowNumber() == 1)
			{
				int firstLineIndent = (int) StyleConstants
						.getFirstLineIndent(getAttributes());
				pixelsToAdd -= firstLineIndent;
			}

			int[] spaces = getSpaces(pixelsToAdd, spaceCount);
			int j = 0;
			int shift = 0;
			for (int i = 1; i < cnt; i++)
			{
				View v = getView(i);
				offsets[i] += shift;
				if ((isContainSpace(v)) && (i != cnt - 1))
				{
					offsets[i] += spaces[j];
					spans[i - 1] += spaces[j];
					shift += spaces[j];
					j++;
				}
			}
		}

		protected int[] getSpaces(int space, int cnt)
		{
			int[] result = new int[cnt];
			if (cnt == 0)
				return result;
			int base = space / cnt;
			int rst = space % cnt;

			for (int i = 0; i < cnt; i++)
			{
				result[i] = base;
				if (rst > 0)
				{
					result[i]++;
					rst--;
				}
			}

			return result;
		}

		/*public float getMinimumSpan(int axis)
		{
			if (axis == View.X_AXIS)
			{
				AttributeSet attr = getAttributes();
				if (StyleConstants.getAlignment(attr) != StyleConstants.ALIGN_JUSTIFIED)
				{
					return super.getMinimumSpan(axis);
				}
				else
				{
					return this.getParent().getMinimumSpan(axis);
				}
			}
			else
			{
				return super.getMinimumSpan(axis);
			}
		}*/

		public float getMaximumSpan(int axis)
		{
			if (axis == View.X_AXIS)
			{
				AttributeSet attr = getAttributes();
				if (StyleConstants.getAlignment(attr) != StyleConstants.ALIGN_JUSTIFIED)
				{
					return super.getMaximumSpan(axis);
				}
				else
				{
					return this.getParent().getMaximumSpan(axis);
				}
			}
			else
			{
				return super.getMaximumSpan(axis);
			}
		}

		public float getPreferredSpan(int axis)
		{
			if (axis == View.X_AXIS)
			{
				AttributeSet attr = getAttributes();
				if (StyleConstants.getAlignment(attr) != StyleConstants.ALIGN_JUSTIFIED)
				{
					return super.getPreferredSpan(axis);
				}
				else
				{
					return this.getParent().getMaximumSpan(axis);
				}
			}
			else
			{
				return super.getPreferredSpan(axis);
			}
		}

		public void setRowNumber(int value)
		{
			rowNumber = value;
		}

		public int getRowNumber()
		{
			return rowNumber;
		}
	}

	class FloatedElement
	{
		protected Element element;
		private int side;
		private int start = -1;
		private int span = -1;
		
		private FloatedElement(Element el)
		{
			this.element=el;
			String value = (String)el.getAttributes().getAttribute(CSS.Attribute.FLOAT);
			value=value.toLowerCase();
			if (value.equals("left"))
			{
				side=LEFT;
			}
			else if (value.equals("right"))
			{
				side=RIGHT;
			}
		}
		
		public void paint(Graphics g, Rectangle a)
		{
			g.setColor(Color.BLACK);
			g.fillRect(a.x,a.y,a.width,a.height);
		}
		
		public int getOffset()
		{
			return element.getStartOffset();
		}
		
		public int getSide()
		{
			return side;
		}
		
		public void setStart(int pos)
		{
			start=pos;
		}
		
		public int getStart()
		{
			return start;
		}
		
		public void setEnd(int span)
		{
			this.span=span;
		}
		
		public int getEnd()
		{
			return span;
		}
		
		public int getIntrinsicWidth()
		{
			return 0;
		}
		
		public int getIntrinsicHeight()
		{
			return 0;
		}
		
		public int getWidth()
		{
			if (element.getAttributes().isDefined(CSS.Attribute.WIDTH))
			{
				return Integer.parseInt((String)element.getAttributes().getAttribute(CSS.Attribute.WIDTH));
			}
			return getIntrinsicWidth();
		}
		
		public int getHeight()
		{
			if (element.getAttributes().isDefined(CSS.Attribute.HEIGHT))
			{
				return Integer.parseInt((String)element.getAttributes().getAttribute(CSS.Attribute.HEIGHT));
			}
			return getIntrinsicHeight();
		}
	}
	
	class FloatedImage extends FloatedElement
	{
		public FloatedImage(Element el)
		{
			super(el);
		}
		
		public int getIntrinsicWidth()
		{
			return 100;
		}
		
		public int getIntrinsicHeight()
		{
			return 50;
		}
	}
	
	private List images = new ArrayList();
	
	public WebEditParagraphView(Element elem)
	{
		super(elem);
		strategy = new AdvancedFlowStrategy();
	}

	public void paint(Graphics g, Shape a)
	{
		super.paint(g, a);
		Iterator it = images.iterator();
		Rectangle alloc = (a instanceof Rectangle) ? (Rectangle) a : a.getBounds();
		Rectangle tempRect = new Rectangle();
		while (it.hasNext())
		{
			FloatedElement image = (FloatedElement)it.next();
			if (image.getStart()>0)
			{
				int x = alloc.x+getLeftInset();
				int y = alloc.y+getTopInset();
				Rectangle clip = g.getClipBounds();
				if (image.getSide()==LEFT)
				{
					tempRect.x = x;
				}
				else
				{
					tempRect.x = alloc.x+alloc.width-image.getWidth();
				}
				tempRect.y = y+getOffset(Y_AXIS, image.getStart());
				tempRect.width = image.getWidth();
				tempRect.height = image.getHeight();
				if (tempRect.intersects(clip))
				{
					image.paint(g, tempRect);
				}
			}
		}
	}
	
	private void buildImageList()
	{
		images.clear();
		buildImageList(getElement());
	}
	
	void invalidateImages()
	{
		Iterator it = images.iterator();
		while (it.hasNext())
		{
			FloatedElement image = (FloatedElement)it.next();
			image.setEnd(-1);
			image.setStart(-1);
		}
	}
	
	void layoutImages(int rowIndex)
	{
		Iterator it = images.iterator();
		while (it.hasNext())
		{
			FloatedElement image = (FloatedElement)it.next();
			if (image.getStart()==-1)
			{
				int pos = getViewIndex(image.getOffset(),Position.Bias.Forward);
				if (pos==rowIndex)
				{
					image.setStart(pos+1);
					log.info("Calculated image starts at "+(pos+1));
				}
			}
			else if (image.getEnd()==-1)
			{
				int sum = 0;
				for (int i=image.getStart(); i<=rowIndex; i++)
				{
					sum+=getView(i).getPreferredSpan(Y_AXIS);
					if (sum>=image.getHeight())
					{
						image.setEnd(i);
						log.info("Calculated image ends at "+i);
						break;
					}
				}
			}
		}
	}
	
	private void buildImageList(Element el)
	{
		if (el.getAttributes().getAttribute(StyleConstants.NameAttribute)==HTML.Tag.IMG)
		{
			if (el.getAttributes().isDefined(CSS.Attribute.FLOAT))
			{
				images.add(new FloatedImage(el));
			}
		}
		else
		{
			for (int i=0; i<el.getElementCount(); i++)
			{
				buildImageList(el.getElement(i));
			}
		}
	}

	protected static int getSpaceCount(String content)
	{
		int result = 0;
		int index = content.indexOf(' ');
		while (index >= 0)
		{
			result++;
			index = content.indexOf(' ', index + 1);
		}
		return result;
	}

	protected static int[] getSpaceIndexes(String content, int shift)
	{
		int cnt = getSpaceCount(content);
		int[] result = new int[cnt];
		int counter = 0;
		int index = content.indexOf(' ');
		while (index >= 0)
		{
			result[counter] = index + shift;
			counter++;
			index = content.indexOf(' ', index + 1);
		}
		return result;
	}

	protected static boolean isContainSpace(View v)
	{
		int startOffset = v.getStartOffset();
		int len = v.getEndOffset() - startOffset;
		try
		{
			String text = v.getDocument().getText(startOffset, len);
			if (text.indexOf(' ') >= 0)
				return true;
			else
				return false;
		}
		catch (Exception ex)
		{
			return false;
		}
	}

	public void setParent(View view)
	{
		super.setParent(view);
		buildImageList();
	}
	
	public void insertUpdate(DocumentEvent ev, Shape a, ViewFactory f)
	{
		super.insertUpdate(ev,a,f);
		buildImageList();
	}
	
	public void removeUpdate(DocumentEvent ev, Shape a, ViewFactory f)
	{
		super.removeUpdate(ev,a,f);
		buildImageList();
	}
	
	public void changedUpdate(DocumentEvent ev, Shape a, ViewFactory f)
	{
		super.changedUpdate(ev,a,f);
		buildImageList();
	}
	
	protected View createRow()
	{
		Element elem = getElement();
		return new AdvancedRow(elem);
	}

	public int getFlowSpan(int index)
	{
		int span = super.getFlowSpan(index);
		Iterator it = images.iterator();
		while (it.hasNext())
		{
			FloatedElement image = (FloatedElement)it.next();
			int start = image.getStart();
			if (start>=0)
			{
				int end = image.getEnd();
				if (end<0)
				{
					end=getViewCount();
				}
				if ((start<=index)&&(end>=index))
				{
					span-=image.getWidth();
				}
			}
		}
		if (index == 0)
		{
			int firstLineIdent = (int) StyleConstants.getFirstLineIndent(this
					.getAttributes());
			span -= firstLineIdent;
		}
		if (index==0)
		{
			log.info("Span is "+span);
		}
		return span;
	}
	
	protected void layout(int width, int height)
	{
		log.info("Layout "+width+" "+height);
		super.layout(width,height);
	}
	
  /*protected SizeRequirements calculateMinorAxisRequirements(int axis, SizeRequirements r)
	{
		if (r==null)
		{
			r = new SizeRequirements();
		}
		float pref = layoutPool.getPreferredSpan(axis);
		float min = layoutPool.getMinimumSpan(axis);
		r.minimum = (int) min;
		r.preferred = Math.max(r.minimum, (int) pref);
		r.maximum = Short.MAX_VALUE;
		r.alignment = 0.5f;
		
		int n = getViewCount();
		if (n>0)
		{
			int[] padding = new int[n];
			for (int i = 0; i<n; i++)
			{
				padding[i] = 0;
			}
			Iterator it = images.iterator();
			while (it.hasNext())
			{
				FloatedImage image = (FloatedImage) it.next();
				int start = image.getStart();
				if (start>=0)
				{
					int end = image.getEnd();
					if (end<0)
					{
						end = n-1;
					}
					for (int i = start; i<=end; i++)
					{
						padding[i] += image.getWidth();
					}
				}
			}
			for (int i = 0; i<n; i++)
			{
				View view = getView(i);
				r.minimum = Math.max(r.minimum, (int)view.getMinimumSpan(axis)+padding[i]);
			}
		}
		r.preferred=Math.max(r.preferred,r.minimum);
		r.maximum=Math.max(r.maximum,r.preferred);
		log.info("MinorReqs: "+r.minimum+","+r.preferred+","+r.maximum);
		return r;
	}*/
  
  /*protected SizeRequirements calculateMajorAxisRequirements(int axis, SizeRequirements r)
  {
  	r = super.calculateMajorAxisRequirements(axis,r);
  	return r;
  }*/
  
  protected void layoutMinorAxis(int targetSpan, int axis, int[] offsets, int[] spans)
	{
  	log.info("Layout span is "+targetSpan);
		int n = getViewCount();
		for (int i = 0; i<n; i++)
		{
			offsets[i] = 0;
			spans[i] = targetSpan;
		}
		Iterator it = images.iterator();
		while (it.hasNext())
		{
			FloatedElement image = (FloatedElement) it.next();
			int start = image.getStart();
			if (start>=0)
			{
				int end = image.getEnd();
				if (end<0)
				{
					end=n-1;
				}
				for (int i = start; i<=end; i++)
				{
					if (image.getSide()==LEFT)
					{
						offsets[i] += image.getWidth();
					}
					spans[i] -= image.getWidth();
				}
			}
		}
		for (int i = 0; i<n; i++)
		{
			View v = getView(i);
			int min = (int) v.getMinimumSpan(axis);
			int max = (int) v.getMaximumSpan(axis);
			if (max<spans[i])
			{
				// can't make the child this wide, align it
				float align = v.getAlignment(axis);
				offsets[i]+=(int)((targetSpan-max)*align);
				spans[i]=max;
			}
			else
			{
				// make it the target width, or as small as it can get.
				spans[i] = Math.max(min, spans[i]);
			}
		}
	}
  
	public int getFlowStart(int index)
	{
		int len = super.getFlowStart(index);
		Iterator it = images.iterator();
		while (it.hasNext())
		{
			FloatedElement image = (FloatedElement)it.next();
			if (image.getSide()==LEFT)
			{
				int start = image.getStart();
				if (start>=0)
				{
					int end = image.getEnd();
					if ((start<=index)&&(end>=index))
					{
						len+=image.getWidth();
					}
				}
			}
		}
		if (index==0)
		{
			len+=(int)StyleConstants.getFirstLineIndent(getAttributes());
		}
		if (index==0)
		{
			log.info("Start is "+len);
		}
		return len;
	}
}

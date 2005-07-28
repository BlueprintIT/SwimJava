/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.htmlkit;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.SizeRequirements;
import javax.swing.text.Document;
import javax.swing.text.GlyphView;
import javax.swing.text.Position;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.Element;
import javax.swing.text.FlowView;
import javax.swing.text.TabExpander;
import javax.swing.text.TabableView;
import javax.swing.text.html.CSS;
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
			WebEditParagraphView wv = (WebEditParagraphView) fv;

			log.info("layout");
			wv.clearImages();

			int pos = fv.getStartOffset();
			int end = fv.getEndOffset();

			// we want to preserve all views from the logicalView from being
			// removed
			View lv = getLogicalView(fv);
			int n = lv.getViewCount();
			for (int i = 0; i<n; i++)
			{
				View v = lv.getView(i);
				v.setParent(lv);
			}
			fv.removeAll();
			for (int rowIndex = 0; pos<end; rowIndex++)
			{
				View row = wv.createRow();
				wv.append(row);

				// layout the row to the current span. If nothing fits,
				// force something.
				int next = layoutRow(wv, rowIndex, pos);
				if (row.getViewCount()==0)
				{
					row.append(createView(wv, pos, Integer.MAX_VALUE, rowIndex));
					next = row.getEndOffset();
				}
				if (next<=pos)
				{
					throw new RuntimeException("infinite loop in formatting");
				}
				else
				{
					pos = next;
				}
			}
			wv.layoutImages(wv.getViewCount()-1);
			/*AttributeSet attr = wv.getAttributes();
			float lineSpacing = StyleConstants.getLineSpacing(attr);
			boolean justifiedAlignment = (StyleConstants.getAlignment(attr)==StyleConstants.ALIGN_JUSTIFIED);
			if (!(justifiedAlignment||(lineSpacing>1)))
			{
				return;
			}

			int cnt = wv.getViewCount();
			for (int i = 0; i<cnt-1; i++)
			{
				AdvancedRow row = (AdvancedRow) wv.getView(i);

				if (lineSpacing>1)
				{
					float height = row.getMinimumSpan(View.Y_AXIS);
					float addition = (height*lineSpacing)-height;
					if (addition>0)
					{
						row.setInsets(row.getTopInset(), row.getLeftInset(),
								(short) addition, row.getRightInset());
					}
				}

				if (justifiedAlignment)
				{
					restructureRow(row, i);
					row.setRowNumber(i+1);
				}
			}*/
		}

		protected int layoutRow(WebEditParagraphView fv, int rowIndex, int pos)
		{
			if (rowIndex>0)
				fv.layoutImages(rowIndex-1);

			log.info("Layout row "+rowIndex);
			final int flowAxis = fv.getFlowAxis();
			View row = fv.getView(rowIndex);

			int imageRow=rowIndex;

			int span = 0;
			int indent = fv.getFlowStart(rowIndex);
			int totalSpan = fv.getFlowSpan(rowIndex);
			int remaining = totalSpan;
			int end = fv.getEndOffset();
			
			boolean justified = (StyleConstants.getAlignment(fv.getAttributes())==StyleConstants.ALIGN_JUSTIFIED);
			
			TabExpander te = (fv instanceof TabExpander) ? (TabExpander) fv : null;

			boolean forcedBreak = false;
			while (pos<end&&remaining>0)
			{
				View v = createView(fv, pos, remaining, rowIndex);
				if (v==null)
				{
					break;
				}
				
				int chunkSpan;
				
				if ((v instanceof AnchorView)&&(((AnchorView)v).isDisplayingAnchor()))
				{
					fv.addImageAnchor((AnchorView)v, imageRow);
					pos=v.getEndOffset();
					chunkSpan = (int)v.getPreferredSpan(flowAxis);
					if (imageRow==rowIndex)
					{
						indent = fv.getFlowStart(imageRow);
						totalSpan = fv.getFlowSpan(rowIndex);
					}
				}
				else
				{
					imageRow=rowIndex+1;

					if (justified)
					{
						Document doc = fv.getDocument();
						try
						{
							int st = v.getStartOffset();
							String content = doc.getText(st,v.getEndOffset()-st);
							int spc = content.indexOf(" ");
							if (spc>=0)
							{
								//log.info("Splitting view at character "+(v.getStartOffset()+spc+1));
								v=v.createFragment(v.getStartOffset(),v.getStartOffset()+spc+1);
							}
						}
						catch (BadLocationException e)
						{
							log.error("This should never happen",e);
						}
					}
					
					if ((flowAxis==X_AXIS)&&(v instanceof TabableView))
					{
						chunkSpan = (int) ((TabableView) v).getTabbedSpan(indent+span, te);
					}
					else
					{
						chunkSpan = (int) v.getPreferredSpan(flowAxis);
					}

					if (v.getBreakWeight(flowAxis, indent+span, remaining)>=ForcedBreakWeight)
					{
						int n = row.getViewCount();
						if (n>0)
						{
							v = v.breakView(flowAxis, pos, indent+span, remaining);
							if (v!=null)
							{
								if ((flowAxis==X_AXIS)&&(v instanceof TabableView))
								{
									chunkSpan = (int) ((TabableView) v).getTabbedSpan(indent+span, te);
								}
								else
								{
									chunkSpan = (int) v.getPreferredSpan(flowAxis);
								}
							}
							else
							{
								chunkSpan = 0;
							}
						}
						forcedBreak = true;
					}	
				}

				span += chunkSpan;
				remaining = totalSpan-span;
				if (v!=null)
				{
					//log.info("Adding view "+v.getStartOffset()+" - "+v.getEndOffset());
					row.append(v);
					pos = v.getEndOffset();
				}
				if (forcedBreak)
				{
					break;
				}

			}
			if (remaining<0)
			{
				// This row is too long and needs to be adjusted.
				adjustRow(fv, rowIndex, totalSpan, indent);
			}
			else if (row.getViewCount()==0)
			{
				// Impossible spec... put in whatever is left.
				View v = createView(fv, pos, Integer.MAX_VALUE, rowIndex);
				row.append(v);
			}
			//log.info("Row "+rowIndex+" became "+row.getViewCount()+" views");
			return row.getEndOffset();
		}
	}

	class AdvancedRow extends BoxView
	{
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
					|| (axis != View.X_AXIS))
			{
				return;
			}
			int count = offsets.length;

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
			
			boolean[] hasSpace = new boolean[count];
			
			int span = 0;
			int spaces = 0;
			for (int i = 0;  i<count; i++)
			{
				span += spans[i];
				View v = getView(i);
				if (v instanceof GlyphView)
				{
					int endchar = (v.getEndOffset()-startOffset)-1;
					if (context.charAt(endchar)==' ')
					{
						spaces++;
						hasSpace[i]=true;
						continue;
					}
				}
				hasSpace[i]=false;
			}
			if (context.charAt(context.length()-1)==' ')
			{
				spaces--;
			}
			
			if (spaces==0)
			{
				return;
			}
		
			double gap = (targetSpan - span)/(1.0*spaces);
			double pixelsToAdd = 0;
			for (int i=0; i<count; i++)
			{
				offsets[i]+=pixelsToAdd;
				if (hasSpace[i])
				{
					pixelsToAdd+=gap;
				}
			}
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
	}

	class FloatedElement
	{
		protected View view;
		private int side;
		private int start = -1;
		private int span = -1;
		
		private FloatedElement(View v)
		{
			this.view=v;
			String value = (String)view.getAttributes().getAttribute(CSS.Attribute.FLOAT);
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
			view.paint(g,a);
		}
		
		public int getOffset()
		{
			return view.getStartOffset();
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
			return (int)view.getPreferredSpan(View.X_AXIS);
		}
		
		public int getIntrinsicHeight()
		{
			return (int)view.getPreferredSpan(View.Y_AXIS);
		}
		
		public int getWidth()
		{
			if (view.getAttributes().isDefined(CSS.Attribute.WIDTH))
			{
				return Integer.parseInt((String)view.getAttributes().getAttribute(CSS.Attribute.WIDTH));
			}
			return getIntrinsicWidth();
		}
		
		public int getHeight()
		{
			if (view.getAttributes().isDefined(CSS.Attribute.HEIGHT))
			{
				return Integer.parseInt((String)view.getAttributes().getAttribute(CSS.Attribute.HEIGHT));
			}
			return getIntrinsicHeight();
		}
	}
	
	class FloatedImage extends FloatedElement
	{
		public FloatedImage(AnchorView anchor)
		{
			super(anchor.getImageView());
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
	
	void clearImages()
	{
		images.clear();
	}
	
	void addImageAnchor(AnchorView anchor, int row)
	{
		FloatedElement el = new FloatedImage(anchor);
		el.setStart(row);
		images.add(el);
		log.info("Added image at "+row);
	}
	
	void layoutImages(int rowIndex)
	{
		Iterator it = images.iterator();
		while (it.hasNext())
		{
			FloatedElement image = (FloatedElement)it.next();
			if (image.getEnd()==-1)
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
			//log.info("Span is "+span);
		}
		return span;
	}
	
	protected void layout(int width, int height)
	{
		//log.info("Layout "+width+" "+height);
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
  	//log.info("Layout span is "+targetSpan);
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
				offsets[i]+=(int)((spans[i]-max)*align);
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
			//log.info("Start is "+len);
		}
		return len;
	}
}

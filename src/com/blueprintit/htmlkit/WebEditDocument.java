package com.blueprintit.htmlkit;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.Segment;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import javax.swing.undo.UndoableEdit;

import org.apache.log4j.Logger;

public class WebEditDocument extends HTMLDocument
{
	private Logger log = Logger.getLogger(this.getClass());

	private WebEditElementBuffer webuffer;
	
	public class WebEditElementBuffer extends ElementBuffer
	{
		public WebEditElementBuffer(Element root)
		{
			super(root);
	    this.root = root;
	    changes = new Vector();
	    path = new Stack();
		}

		/**
		 * Gets the root element.
		 * 
		 * @return the root element
		 */
		public Element getRootElement()
		{
			return root;
		}

		/**
		 * Inserts new content.
		 * 
		 * @param offset
		 *          the starting offset >= 0
		 * @param length
		 *          the length >= 0
		 * @param data
		 *          the data to insert
		 * @param de
		 *          the event capturing this edit
		 */
		public void insert(int offset, int length, ElementSpec[] data,
				DefaultDocumentEvent de)
		{
			if (length==0)
			{
				// Nothing was inserted, no structure change.
				return;
			}
			insertOp = true;
			beginEdits(offset, length);
			insertUpdate(data);
			endEdits(de);

			insertOp = false;
		}

		void create(int length, ElementSpec[] data, DefaultDocumentEvent de)
		{
			insertOp = true;
			beginEdits(offset, length);

			// PENDING(prinz) this needs to be fixed to create a new
			// root element as well, but requires changes to the
			// DocumentEvent to inform the views that there is a new
			// root element.

			// Recreate the ending fake element to have the correct offsets.
			Element elem = root;
			int index = elem.getElementIndex(0);
			while (!elem.isLeaf())
			{
				Element child = elem.getElement(index);
				push(elem, index);
				elem = child;
				index = elem.getElementIndex(0);
			}
			ElemChanges ec = (ElemChanges) path.peek();
			Element child = ec.parent.getElement(ec.index);
			ec.added.addElement(createLeafElement(ec.parent, child.getAttributes(),
					getLength(), child.getEndOffset()));
			ec.removed.addElement(child);
			while (path.size()>1)
			{
				pop();
			}

			int n = data.length;

			// Reset the root elements attributes.
			AttributeSet newAttrs = null;
			if (n>0&&data[0].getType()==ElementSpec.StartTagType)
			{
				newAttrs = data[0].getAttributes();
			}
			if (newAttrs==null)
			{
				newAttrs = SimpleAttributeSet.EMPTY;
			}
			MutableAttributeSet attr = (MutableAttributeSet) root.getAttributes();
			de.addEdit(new AttributeUndoableEdit(root, newAttrs, true));
			attr.removeAttributes(attr);
			attr.addAttributes(newAttrs);

			// fold in the specified subtree
			for (int i = 1; i<n; i++)
			{
				insertElement(data[i]);
			}

			// pop the remaining path
			while (path.size()!=0)
			{
				pop();
			}

			endEdits(de);
			insertOp = false;
		}

		/**
		 * Removes content.
		 * 
		 * @param offset
		 *          the starting offset >= 0
		 * @param length
		 *          the length >= 0
		 * @param de
		 *          the event capturing this edit
		 */
		public void remove(int offset, int length, DefaultDocumentEvent de)
		{
			beginEdits(offset, length);
			removeUpdate();
			endEdits(de);
		}

		/**
		 * Changes content.
		 * 
		 * @param offset
		 *          the starting offset >= 0
		 * @param length
		 *          the length >= 0
		 * @param de
		 *          the event capturing this edit
		 */
		public void change(int offset, int length, DefaultDocumentEvent de)
		{
			beginEdits(offset, length);
			changeUpdate();
			endEdits(de);
		}

		/**
		 * Inserts an update into the document.
		 * 
		 * @param data
		 *          the elements to insert
		 */
		protected void insertUpdate(ElementSpec[] data)
		{
			// push the path
			Element elem = root;
			int index = elem.getElementIndex(offset);
			while (!elem.isLeaf())
			{
				Element child = elem.getElement(index);
				push(elem, (child.isLeaf() ? index : index+1));
				elem = child;
				index = elem.getElementIndex(offset);
			}

			// Build a copy of the original path.
			insertPath = new ElemChanges[path.size()];
			path.copyInto(insertPath);

			// Haven't created the fracture yet.
			createdFracture = false;

			// Insert the first content.
			int i;

			recreateLeafs = false;
			if (data[0].getType()==ElementSpec.ContentType)
			{
				insertFirstContent(data);
				pos += data[0].getLength();
				i = 1;
			}
			else
			{
				fractureDeepestLeaf(data);
				i = 0;
			}

			// fold in the specified subtree
			int n = data.length;
			for (; i<n; i++)
			{
				insertElement(data[i]);
			}

			// Fracture, if we haven't yet.
			if (!createdFracture)
				fracture(-1);

			// pop the remaining path
			while (path.size()!=0)
			{
				pop();
			}

			// Offset the last index if necessary.
			if (offsetLastIndex&&offsetLastIndexOnReplace)
			{
				insertPath[insertPath.length-1].index++;
			}

			// Make sure an edit is going to be created for each of the
			// original path items that have a change.
			for (int counter = insertPath.length-1; counter>=0; counter--)
			{
				ElemChanges change = insertPath[counter];
				if (change.parent==fracturedParent)
					change.added.addElement(fracturedChild);
				if ((change.added.size()>0||change.removed.size()>0)
						&&!changes.contains(change))
				{
					// PENDING(sky): Do I need to worry about order here?
					changes.addElement(change);
				}
			}

			// An insert at 0 with an initial end implies some elements
			// will have no children (the bottomost leaf would have length 0)
			// this will find what element need to be removed and remove it.
			if (offset==0&&fracturedParent!=null
					&&data[0].getType()==ElementSpec.EndTagType)
			{
				int counter = 0;
				while (counter<data.length
								&&data[counter].getType()==ElementSpec.EndTagType)
				{
					counter++;
				}
				ElemChanges change = insertPath[insertPath.length-counter-1];
				change.removed.insertElementAt(
						change.parent.getElement(--change.index), 0);
			}
		}

		/**
		 * Updates the element structure in response to a removal from the
		 * associated sequence in the document. Any elements consumed by the span of
		 * the removal are removed.
		 */
		protected void removeUpdate()
		{
			removeElements(root, offset, offset+length);
		}

		/**
		 * Updates the element structure in response to a change in the document.
		 */
		protected void changeUpdate()
		{
			boolean didEnd = split(offset, length);
			if (!didEnd)
			{
				// need to do the other end
				while (path.size()!=0)
				{
					pop();
				}
				split(offset+length, 0);
			}
			while (path.size()!=0)
			{
				pop();
			}
		}

		boolean split(int offs, int len)
		{
			boolean splitEnd = false;
			// push the path
			Element e = root;
			int index = e.getElementIndex(offs);
			while (!e.isLeaf())
			{
				push(e, index);
				e = e.getElement(index);
				index = e.getElementIndex(offs);
			}

			ElemChanges ec = (ElemChanges) path.peek();
			Element child = ec.parent.getElement(ec.index);
			// make sure there is something to do... if the
			// offset is already at a boundary then there is
			// nothing to do.
			if (child.getStartOffset()<offs&&offs<child.getEndOffset())
			{
				// we need to split, now see if the other end is within
				// the same parent.
				int index0 = ec.index;
				int index1 = index0;
				if (((offs+len)<ec.parent.getEndOffset())&&(len!=0))
				{
					// it's a range split in the same parent
					index1 = ec.parent.getElementIndex(offs+len);
					if (index1==index0)
					{
						// it's a three-way split
						ec.removed.addElement(child);
						e = createLeafElement(ec.parent, child.getAttributes(), child
								.getStartOffset(), offs);
						ec.added.addElement(e);
						e = createLeafElement(ec.parent, child.getAttributes(), offs, offs
																																					+len);
						ec.added.addElement(e);
						e = createLeafElement(ec.parent, child.getAttributes(), offs+len,
								child.getEndOffset());
						ec.added.addElement(e);
						return true;
					}
					else
					{
						child = ec.parent.getElement(index1);
						if ((offs+len)==child.getStartOffset())
						{
							// end is already on a boundary
							index1 = index0;
						}
					}
					splitEnd = true;
				}

				// split the first location
				pos = offs;
				child = ec.parent.getElement(index0);
				ec.removed.addElement(child);
				e = createLeafElement(ec.parent, child.getAttributes(), child
						.getStartOffset(), pos);
				ec.added.addElement(e);
				e = createLeafElement(ec.parent, child.getAttributes(), pos, child
						.getEndOffset());
				ec.added.addElement(e);

				// pick up things in the middle
				for (int i = index0+1; i<index1; i++)
				{
					child = ec.parent.getElement(i);
					ec.removed.addElement(child);
					ec.added.addElement(child);
				}

				if (index1!=index0)
				{
					child = ec.parent.getElement(index1);
					pos = offs+len;
					ec.removed.addElement(child);
					e = createLeafElement(ec.parent, child.getAttributes(), child
							.getStartOffset(), pos);
					ec.added.addElement(e);
					e = createLeafElement(ec.parent, child.getAttributes(), pos, child
							.getEndOffset());
					ec.added.addElement(e);
				}
			}
			return splitEnd;
		}

		/**
		 * Creates the UndoableEdit record for the edits made in the buffer.
		 */
		void endEdits(DefaultDocumentEvent de)
		{
			int n = changes.size();
			for (int i = 0; i<n; i++)
			{
				ElemChanges ec = (ElemChanges) changes.elementAt(i);
				Element[] removed = new Element[ec.removed.size()];
				ec.removed.copyInto(removed);
				Element[] added = new Element[ec.added.size()];
				ec.added.copyInto(added);
				int index = ec.index;
				((BranchElement) ec.parent).replace(index, removed.length, added);
				ElementEdit ee = new ElementEdit((BranchElement) ec.parent, index,
						removed, added);
				de.addEdit(ee);
			}

			changes.removeAllElements();
			path.removeAllElements();

			/*
			 * for (int i = 0; i < n; i++) { ElemChanges ec = (ElemChanges)
			 * changes.elementAt(i); System.err.print("edited: " + ec.parent + " at: " +
			 * ec.index + " removed " + ec.removed.size()); if (ec.removed.size() > 0) {
			 * int r0 = ((Element) ec.removed.firstElement()).getStartOffset(); int r1 =
			 * ((Element) ec.removed.lastElement()).getEndOffset();
			 * System.err.print("[" + r0 + "," + r1 + "]"); } System.err.print(" added " +
			 * ec.added.size()); if (ec.added.size() > 0) { int p0 = ((Element)
			 * ec.added.firstElement()).getStartOffset(); int p1 = ((Element)
			 * ec.added.lastElement()).getEndOffset(); System.err.print("[" + p0 + "," +
			 * p1 + "]"); } System.err.println(""); }
			 */
		}

		/**
		 * Initialize the buffer
		 */
		void beginEdits(int offset, int length)
		{
			this.offset = offset;
			this.length = length;
			this.endOffset = offset+length;
			pos = offset;
			if (changes==null)
			{
				changes = new Vector();
			}
			else
			{
				changes.removeAllElements();
			}
			if (path==null)
			{
				path = new Stack();
			}
			else
			{
				path.removeAllElements();
			}
			fracturedParent = null;
			fracturedChild = null;
			offsetLastIndex = offsetLastIndexOnReplace = false;
		}

		/**
		 * Pushes a new element onto the stack that represents the current path.
		 * 
		 * @param record
		 *          Whether or not the push should be recorded as an element change
		 *          or not.
		 * @param isFracture
		 *          true if pushing on an element that was created as the result of
		 *          a fracture.
		 */
		void push(Element e, int index, boolean isFracture)
		{
			ElemChanges ec = new ElemChanges(e, index, isFracture);
			path.push(ec);
		}

		void push(Element e, int index)
		{
			push(e, index, false);
		}

		void pop()
		{
			ElemChanges ec = (ElemChanges) path.peek();
			path.pop();
			if ((ec.added.size()>0)||(ec.removed.size()>0))
			{
				changes.addElement(ec);
			}
			else if (!path.isEmpty())
			{
				Element e = ec.parent;
				if (e.getElementCount()==0)
				{
					// if we pushed a branch element that didn't get
					// used, make sure its not marked as having been added.
					ec = (ElemChanges) path.peek();
					ec.added.removeElement(e);
				}
			}
		}

		/**
		 * move the current offset forward by n.
		 */
		void advance(int n)
		{
			pos += n;
		}

		void insertElement(ElementSpec es)
		{
			ElemChanges ec = (ElemChanges) path.peek();
			switch (es.getType())
			{
				case ElementSpec.StartTagType:
					switch (es.getDirection())
					{
						case ElementSpec.JoinNextDirection:
							// Don't create a new element, use the existing one
							// at the specified location.
							Element parent = ec.parent.getElement(ec.index);

							if (parent.isLeaf())
							{
								// This happens if inserting into a leaf, followed
								// by a join next where next sibling is not a leaf.
								if ((ec.index+1)<ec.parent.getElementCount())
									parent = ec.parent.getElement(ec.index+1);
								else
									throw new Error("Join next to leaf");
							}
							// Not really a fracture, but need to treat it like
							// one so that content join next will work correctly.
							// We can do this because there will never be a join
							// next followed by a join fracture.
							push(parent, 0, true);
							break;
						case ElementSpec.JoinFractureDirection:
							if (!createdFracture)
							{
								// Should always be something on the stack!
								fracture(path.size()-1);
							}
							// If parent isn't a fracture, fracture will be
							// fracturedChild.
							if (!ec.isFracture)
							{
								push(fracturedChild, 0, true);
							}
							else
								// Parent is a fracture, use 1st element.
								push(ec.parent.getElement(0), 0, true);
							break;
						default:
							Element belem = createBranchElement(ec.parent, es.getAttributes());
							ec.added.addElement(belem);
							push(belem, 0);
							break;
					}
					break;
				case ElementSpec.EndTagType:
					pop();
					break;
				case ElementSpec.ContentType:
					int len = es.getLength();
					if (es.getDirection()!=ElementSpec.JoinNextDirection)
					{
						Element leaf = createLeafElement(ec.parent, es.getAttributes(),
								pos, pos+len);
						ec.added.addElement(leaf);
					}
					else
					{
						// JoinNext on tail is only applicable if last element
						// and attributes come from that of first element.
						// With a little extra testing it would be possible
						// to NOT due this again, as more than likely fracture()
						// created this element.
						if (!ec.isFracture)
						{
							Element first = null;
							if (insertPath!=null)
							{
								for (int counter = insertPath.length-1; counter>=0; counter--)
								{
									if (insertPath[counter]==ec)
									{
										if (counter!=(insertPath.length-1))
											first = ec.parent.getElement(ec.index);
										break;
									}
								}
							}
							if (first==null)
								first = ec.parent.getElement(ec.index+1);
							Element leaf = createLeafElement(ec.parent,
									first.getAttributes(), pos, first.getEndOffset());
							ec.added.addElement(leaf);
							ec.removed.addElement(first);
						}
						else
						{
							// Parent was fractured element.
							Element first = ec.parent.getElement(0);
							Element leaf = createLeafElement(ec.parent,
									first.getAttributes(), pos, first.getEndOffset());
							ec.added.addElement(leaf);
							ec.removed.addElement(first);
						}
					}
					pos += len;
					break;
			}
		}

		/**
		 * Remove the elements from <code>elem</code> in range
		 * <code>rmOffs0</code>, <code>rmOffs1</code>. This uses
		 * <code>canJoin</code> and <code>join</code> to handle joining the
		 * endpoints of the insertion.
		 * 
		 * @return true if elem will no longer have any elements.
		 */
		boolean removeElements(Element elem, int rmOffs0, int rmOffs1)
		{
			if (!elem.isLeaf())
			{
				// update path for changes
				int index0 = elem.getElementIndex(rmOffs0);
				int index1 = elem.getElementIndex(rmOffs1);
				push(elem, index0);
				ElemChanges ec = (ElemChanges) path.peek();

				// if the range is contained by one element,
				// we just forward the request
				if (index0==index1)
				{
					Element child0 = elem.getElement(index0);
					if (rmOffs0<=child0.getStartOffset()&&rmOffs1>=child0.getEndOffset())
					{
						// Element totally removed.
						ec.removed.addElement(child0);
					}
					else if (removeElements(child0, rmOffs0, rmOffs1))
					{
						ec.removed.addElement(child0);
					}
				}
				else
				{
					// the removal range spans elements. If we can join
					// the two endpoints, do it. Otherwise we remove the
					// interior and forward to the endpoints.
					Element child0 = elem.getElement(index0);
					Element child1 = elem.getElement(index1);
					boolean containsOffs1 = (rmOffs1<elem.getEndOffset());
					if (containsOffs1&&canJoin(child0, child1, rmOffs0, rmOffs1))
					{
						// remove and join
						for (int i = index0; i<=index1; i++)
						{
							ec.removed.addElement(elem.getElement(i));
						}
						Element[] els = join(elem, child0, child1, rmOffs0, rmOffs1);
						for (int i=0; i<els.length; i++)
							ec.added.addElement(els[i]);
					}
					else
					{
						// remove interior and forward
						int rmIndex0 = index0+1;
						int rmIndex1 = index1-1;
						if (child0.getStartOffset()==rmOffs0||(index0==0&&child0.getStartOffset()>rmOffs0&&child0
										.getEndOffset()<=rmOffs1))
						{
							// start element completely consumed
							child0 = null;
							rmIndex0 = index0;
						}
						if (!containsOffs1)
						{
							child1 = null;
							rmIndex1++;
						}
						else if (child1.getStartOffset()==rmOffs1)
						{
							// end element not touched
							child1 = null;
						}
						if (rmIndex0<=rmIndex1)
						{
							ec.index = rmIndex0;
						}
						for (int i = rmIndex0; i<=rmIndex1; i++)
						{
							ec.removed.addElement(elem.getElement(i));
						}
						if (child0!=null)
						{
							if (removeElements(child0, rmOffs0, rmOffs1))
							{
								ec.removed.insertElementAt(child0, 0);
								ec.index = index0;
							}
						}
						if (child1!=null)
						{
							if (removeElements(child1, rmOffs0, rmOffs1))
							{
								ec.removed.addElement(child1);
							}
						}
					}
				}

				// publish changes
				pop();

				// Return true if we no longer have any children.
				if (elem.getElementCount()==(ec.removed.size()-ec.added.size()))
				{
					return true;
				}
			}
			return false;
		}

		/**
		 * Can the two given elements be coelesced together into one element?
		 */
		boolean canJoin(Element e0, Element e1, int offs0, int offs1)
		{
			if ((e0==null)||(e1==null))
			{
				return false;
			}
			// Don't join a leaf to a branch.
			boolean leaf0 = e0.isLeaf();
			boolean leaf1 = e1.isLeaf();
			if (leaf0!=leaf1)
			{
				return false;
			}
			if (leaf0)
			{
				// Only join leaves if the attributes match, otherwise
				// style information will be lost.
				return e0.getAttributes().isEqual(e1.getAttributes());
			}
			Element left=deepestParagraph(e0,offs0);
			Element right=deepestParagraph(e1,offs1);
			if ((isParagraph(left))&&(isParagraph(right)))
				return true;
			
			// Only join non-leafs if the names are equal. This may result
			// in loss of style information, but this is typically acceptable
			// for non-leafs.
			String name0 = e0.getName();
			String name1 = e1.getName();
			if (name0!=null)
			{
				return name0.equals(name1);
			}
			if (name1!=null)
			{
				return name1.equals(name0);
			}
			// Both names null, treat as equal.
			return true;
		}

		boolean isParagraph(Element el)
		{
			Object o = el.getAttributes().getAttribute(StyleConstants.NameAttribute);
			if (o!=null)
			{
				if ((o==HTML.Tag.P)||(o==HTML.Tag.IMPLIED)
					||(o==HTML.Tag.H1)||(o==HTML.Tag.H2)||(o==HTML.Tag.H3)
					||(o==HTML.Tag.H4)||(o==HTML.Tag.H5)||(o==HTML.Tag.H6))
				{
					return true;
				}
			}
			return false;
		}
		
		Element deepestParagraph(Element el, int offs)
		{
			Element last=el;
			while (!el.isLeaf())
			{
				last=el;
				try
				{
					el=el.getElement(el.getElementIndex(offs));
				}
				catch (RuntimeException e)
				{
					log.debug("Element "+el.getName()+" has "+el.getElementCount());
					throw e;
				}
			}
			return last;
		}
		
		/**
		 * Joins the two elements carving out a hole for the given removed range.
		 */
		Element[] join(Element p, Element left, Element right, int rmOffs0, int rmOffs1)
		{
			if (left.isLeaf()&&right.isLeaf())
			{
				return new Element[] {createLeafElement(p, left.getAttributes(), left.getStartOffset(), right.getEndOffset())};
			}
			else if ((!left.isLeaf())&&(!right.isLeaf()))
			{
				log.debug("Join "+rmOffs0+" "+rmOffs1);

				Element newl=alternateCloneAsNecessary(p,left,rmOffs0,rmOffs1);
				
				Element rightpara=deepestParagraph(right,rmOffs1);
				Element newr;
				if ((rightpara==right)
						||((right.getStartOffset()==rightpara.getStartOffset())
								&&(right.getEndOffset()==rightpara.getEndOffset())))
				{
					newr=null;
				}
				else
				{
					newr=cloneAsNecessary(p,right,rightpara.getStartOffset(),rightpara.getEndOffset());
					if (newr.getElementCount()==0)
					{
						log.debug("Right side now empty");
						newr=null;
					}
				}
				
				BranchElement leftpara=(BranchElement)deepestParagraph(newl,rmOffs0);
				log.info("Joining "+leftpara.getName()+" "+rightpara.getName());
				
				Vector leftchildren = new Vector();
				int ljIndex = leftpara.getElementIndex(rmOffs0);
				Element lj = leftpara.getElement(ljIndex);
				if (lj.getStartOffset()>=rmOffs0)
				{
					lj = null;
				}
				// transfer the left
				for (int i = 0; i<ljIndex; i++)
				{
					leftchildren.addElement(leftpara.getElement(i));
				}
				
				int rjIndex = rightpara.getElementIndex(rmOffs1);
				Element rj = rightpara.getElement(rjIndex);
				if (rj.getStartOffset()==rmOffs1)
				{
					rj = null;
				}
				
				// transfer the join/middle
				if (canJoin(lj, rj, rmOffs0, rmOffs1))
				{
					Element[] els = join(leftpara, lj, rj, rmOffs0, rmOffs1);
					for (int i=0; i<els.length; i++)
						leftchildren.addElement(els[i]);
				}
				else
				{
					if (lj!=null)
					{
						leftchildren.addElement(lj);
					}
					if (rj!=null)
					{
						leftchildren.addElement(cloneAsNecessary(leftpara, rj, rmOffs0, rmOffs1));
					}
				}

				// transfer the right
				int n = rightpara.getElementCount();
				for (int i = (rj==null) ? rjIndex : rjIndex+1; i<n; i++)
				{
					leftchildren.addElement(clone(leftpara, rightpara.getElement(i)));
				}

				// install the children
				Element[] c = new Element[leftchildren.size()];
				leftchildren.copyInto(c);
				leftpara.replace(0, leftpara.getChildCount(), c);

				if (newr!=null)
				{
					return new Element[] {newl,newr};
				}
				else
				{
					return new Element[] {newl};
				}
			}
			else
			{
				throw new Error("No support to join leaf element with non-leaf element");
			}
		}

		/**
		 * Creates a copy of this element, with a different parent.
		 * 
		 * @param parent
		 *          the parent element
		 * @param clonee
		 *          the element to be cloned
		 * @return the copy
		 */
		public Element clone(Element parent, Element clonee)
		{
			if (clonee.isLeaf())
			{
				return createLeafElement(parent, clonee.getAttributes(), clonee
						.getStartOffset(), clonee.getEndOffset());
			}
			Element e = createBranchElement(parent, clonee.getAttributes());
			int n = clonee.getElementCount();
			Element[] children = new Element[n];
			for (int i = 0; i<n; i++)
			{
				children[i] = clone(e, clonee.getElement(i));
			}
			((BranchElement) e).replace(0, 0, children);
			return e;
		}

		/**
		 * Creates a copy of this element, with a different parent. Children of this
		 * element included in the removal range will be discarded.
		 */
		Element cloneAsNecessary(Element parent, Element clonee, int rmOffs0, int rmOffs1)
		{
			if (clonee.isLeaf())
			{
				return createLeafElement(parent, clonee.getAttributes(), clonee
						.getStartOffset(), clonee.getEndOffset());
			}
			Element e = createBranchElement(parent, clonee.getAttributes());
			int n = clonee.getElementCount();
			ArrayList childrenList = new ArrayList(n);
			for (int i = 0; i<n; i++)
			{
				Element elem = clonee.getElement(i);
				if (elem.getStartOffset()<rmOffs0||elem.getEndOffset()>rmOffs1)
				{
					childrenList.add(cloneAsNecessary(e, elem, rmOffs0, rmOffs1));
				}
			}
			Element[] children = new Element[childrenList.size()];
			children = (Element[]) childrenList.toArray(children);
			((BranchElement) e).replace(0, 0, children);
			return e;
		}

		/**
		 * Creates a copy of this element, with a different parent. Children of this
		 * element included in the removal range will be discarded.
		 */
		Element alternateCloneAsNecessary(Element parent, Element clonee, int rmOffs0, int rmOffs1)
		{
			if (clonee.isLeaf())
			{
				return createLeafElement(parent, clonee.getAttributes(), clonee
						.getStartOffset(), clonee.getEndOffset());
			}
			Element e = createBranchElement(parent, clonee.getAttributes());
			int n = clonee.getElementCount();
			ArrayList childrenList = new ArrayList(n);
			for (int i = 0; i<n; i++)
			{
				Element elem = clonee.getElement(i);
				if (elem.getStartOffset()<=rmOffs0)
				{
					childrenList.add(alternateCloneAsNecessary(e, elem, rmOffs0, rmOffs1));
				}
			}
			Element[] children = new Element[childrenList.size()];
			children = (Element[]) childrenList.toArray(children);
			((BranchElement) e).replace(0, 0, children);
			return e;
		}

		/**
		 * Determines if a fracture needs to be performed. A fracture can be thought
		 * of as moving the right part of a tree to a new location, where the right
		 * part is determined by what has been inserted. <code>depth</code> is
		 * used to indicate a JoinToFracture is needed to an element at a depth of
		 * <code>depth</code>. Where the root is 0, 1 is the children of the
		 * root...
		 * <p>
		 * This will invoke <code>fractureFrom</code> if it is determined a
		 * fracture needs to happen.
		 */
		void fracture(int depth)
		{
			int cLength = insertPath.length;
			int lastIndex = -1;
			boolean needRecreate = recreateLeafs;
			ElemChanges lastChange = insertPath[cLength-1];
			// Use childAltered to determine when a child has been altered,
			// that is the point of insertion is less than the element count.
			boolean childAltered = ((lastChange.index+1)<lastChange.parent
					.getElementCount());
			int deepestAlteredIndex = (needRecreate) ? cLength : -1;
			int lastAlteredIndex = cLength-1;

			createdFracture = true;
			// Determine where to start recreating from.
			// Start at - 2, as first one is indicated by recreateLeafs and
			// childAltered.
			for (int counter = cLength-2; counter>=0; counter--)
			{
				ElemChanges change = insertPath[counter];
				if (change.added.size()>0||counter==depth)
				{
					lastIndex = counter;
					if (!needRecreate&&childAltered)
					{
						needRecreate = true;
						if (deepestAlteredIndex==-1)
							deepestAlteredIndex = lastAlteredIndex+1;
					}
				}
				if (!childAltered&&change.index<change.parent.getElementCount())
				{
					childAltered = true;
					lastAlteredIndex = counter;
				}
			}
			if (needRecreate)
			{
				// Recreate all children to right of parent starting
				// at lastIndex.
				if (lastIndex==-1)
					lastIndex = cLength-1;
				fractureFrom(insertPath, lastIndex, deepestAlteredIndex);
			}
		}

		/**
		 * Recreates the elements to the right of the insertion point. This starts
		 * at <code>startIndex</code> in <code>changed</code>, and calls
		 * duplicate to duplicate existing elements. This will also duplicate the
		 * elements along the insertion point, until a depth of
		 * <code>endFractureIndex</code> is reached, at which point only the
		 * elements to the right of the insertion point are duplicated.
		 */
		void fractureFrom(ElemChanges[] changed, int startIndex,
				int endFractureIndex)
		{
			// Recreate the element representing the inserted index.
			ElemChanges change = changed[startIndex];
			Element child;
			Element newChild;
			int changeLength = changed.length;

			if ((startIndex+1)==changeLength)
				child = change.parent.getElement(change.index);
			else
				child = change.parent.getElement(change.index-1);
			if (child.isLeaf())
			{
				newChild = createLeafElement(change.parent, child.getAttributes(), Math
						.max(endOffset, child.getStartOffset()), child.getEndOffset());
			}
			else
			{
				newChild = createBranchElement(change.parent, child.getAttributes());
			}
			fracturedParent = change.parent;
			fracturedChild = newChild;

			// Recreate all the elements to the right of the
			// insertion point.
			Element parent = newChild;

			while (++startIndex<endFractureIndex)
			{
				boolean isEnd = ((startIndex+1)==endFractureIndex);
				boolean isEndLeaf = ((startIndex+1)==changeLength);

				// Create the newChild, a duplicate of the elment at
				// index. This isn't done if isEnd and offsetLastIndex are true
				// indicating a join previous was done.
				change = changed[startIndex];

				// Determine the child to duplicate, won't have to duplicate
				// if at end of fracture, or offseting index.
				if (isEnd)
				{
					if (offsetLastIndex||!isEndLeaf)
						child = null;
					else
						child = change.parent.getElement(change.index);
				}
				else
				{
					child = change.parent.getElement(change.index-1);
				}
				// Duplicate it.
				if (child!=null)
				{
					if (child.isLeaf())
					{
						newChild = createLeafElement(parent, child.getAttributes(), Math
								.max(endOffset, child.getStartOffset()), child.getEndOffset());
					}
					else
					{
						newChild = createBranchElement(parent, child.getAttributes());
					}
				}
				else
					newChild = null;

				// Recreate the remaining children (there may be none).
				int kidsToMove = change.parent.getElementCount()-change.index;
				Element[] kids;
				int moveStartIndex;
				int kidStartIndex = 1;

				if (newChild==null)
				{
					// Last part of fracture.
					if (isEndLeaf)
					{
						kidsToMove--;
						moveStartIndex = change.index+1;
					}
					else
					{
						moveStartIndex = change.index;
					}
					kidStartIndex = 0;
					kids = new Element[kidsToMove];
				}
				else
				{
					if (!isEnd)
					{
						// Branch.
						kidsToMove++;
						moveStartIndex = change.index;
					}
					else
					{
						// Last leaf, need to recreate part of it.
						moveStartIndex = change.index+1;
					}
					kids = new Element[kidsToMove];
					kids[0] = newChild;
				}

				for (int counter = kidStartIndex; counter<kidsToMove; counter++)
				{
					Element toMove = change.parent.getElement(moveStartIndex++);
					kids[counter] = recreateFracturedElement(parent, toMove);
					change.removed.addElement(toMove);
				}
				((BranchElement) parent).replace(0, 0, kids);
				parent = newChild;
			}
		}

		/**
		 * Recreates <code>toDuplicate</code>. This is called when an element
		 * needs to be created as the result of an insertion. This will recurse and
		 * create all the children. This is similiar to <code>clone</code>, but
		 * deteremines the offsets differently.
		 */
		Element recreateFracturedElement(Element parent, Element toDuplicate)
		{
			if (toDuplicate.isLeaf())
			{
				return createLeafElement(parent, toDuplicate.getAttributes(), Math.max(
						toDuplicate.getStartOffset(), endOffset), toDuplicate
						.getEndOffset());
			}
			// Not a leaf
			Element newParent = createBranchElement(parent, toDuplicate
					.getAttributes());
			int childCount = toDuplicate.getElementCount();
			Element[] newKids = new Element[childCount];
			for (int counter = 0; counter<childCount; counter++)
			{
				newKids[counter] = recreateFracturedElement(newParent, toDuplicate
						.getElement(counter));
			}
			((BranchElement) newParent).replace(0, 0, newKids);
			return newParent;
		}

		/**
		 * Splits the bottommost leaf in <code>path</code>. This is called from
		 * insert when the first element is NOT content.
		 */
		void fractureDeepestLeaf(ElementSpec[] specs)
		{
			// Split the bottommost leaf. It will be recreated elsewhere.
			ElemChanges ec = (ElemChanges) path.peek();
			Element child = ec.parent.getElement(ec.index);
			// Inserts at offset 0 do not need to recreate child (it would
			// have a length of 0!).
			if (offset!=0)
			{
				Element newChild = createLeafElement(ec.parent, child.getAttributes(),
						child.getStartOffset(), offset);

				ec.added.addElement(newChild);
			}
			ec.removed.addElement(child);
			if (child.getEndOffset()!=endOffset)
				recreateLeafs = true;
			else
				offsetLastIndex = true;
		}

		/**
		 * Inserts the first content. This needs to be separate to handle joining.
		 */
		void insertFirstContent(ElementSpec[] specs)
		{
			ElementSpec firstSpec = specs[0];
			ElemChanges ec = (ElemChanges) path.peek();
			Element child = ec.parent.getElement(ec.index);
			int firstEndOffset = offset+firstSpec.getLength();
			boolean isOnlyContent = (specs.length==1);

			switch (firstSpec.getDirection())
			{
				case ElementSpec.JoinPreviousDirection:
					if (child.getEndOffset()!=firstEndOffset&&!isOnlyContent)
					{
						// Create the left split part containing new content.
						Element newE = createLeafElement(ec.parent, child.getAttributes(),
								child.getStartOffset(), firstEndOffset);
						ec.added.addElement(newE);
						ec.removed.addElement(child);
						// Remainder will be created later.
						if (child.getEndOffset()!=endOffset)
							recreateLeafs = true;
						else
							offsetLastIndex = true;
					}
					else
					{
						offsetLastIndex = true;
						offsetLastIndexOnReplace = true;
					}
					// else Inserted at end, and is total length.
					// Update index incase something added/removed.
					break;
				case ElementSpec.JoinNextDirection:
					if (offset!=0)
					{
						// Recreate the first element, its offset will have
						// changed.
						Element newE = createLeafElement(ec.parent, child.getAttributes(),
								child.getStartOffset(), offset);
						ec.added.addElement(newE);
						// Recreate the second, merge part. We do no checking
						// to see if JoinNextDirection is valid here!
						Element nextChild = ec.parent.getElement(ec.index+1);
						if (isOnlyContent)
							newE = createLeafElement(ec.parent, nextChild.getAttributes(),
									offset, nextChild.getEndOffset());
						else
							newE = createLeafElement(ec.parent, nextChild.getAttributes(),
									offset, firstEndOffset);
						ec.added.addElement(newE);
						ec.removed.addElement(child);
						ec.removed.addElement(nextChild);
					}
					// else nothin to do.
					// PENDING: if !isOnlyContent could raise here!
					break;
				default:
					// Inserted into middle, need to recreate split left
					// new content, and split right.
					if (child.getStartOffset()!=offset)
					{
						Element newE = createLeafElement(ec.parent, child.getAttributes(),
								child.getStartOffset(), offset);
						ec.added.addElement(newE);
					}
					ec.removed.addElement(child);
					// new content
					Element newE = createLeafElement(ec.parent,
							firstSpec.getAttributes(), offset, firstEndOffset);
					ec.added.addElement(newE);
					if (child.getEndOffset()!=endOffset)
					{
						// Signals need to recreate right split later.
						recreateLeafs = true;
					}
					else
					{
						offsetLastIndex = true;
					}
					break;
			}
		}

		Element root;

		transient int pos; // current position

		transient int offset;

		transient int length;

		transient int endOffset;

		transient Vector changes; // Vector<ElemChanges>

		transient Stack path; // Stack<ElemChanges>

		transient boolean insertOp;

		transient boolean recreateLeafs; // For insert.

		/** For insert, path to inserted elements. */
		transient ElemChanges[] insertPath;

		/** Only for insert, set to true when the fracture has been created. */
		transient boolean createdFracture;

		/** Parent that contains the fractured child. */
		transient Element fracturedParent;

		/** Fractured child. */
		transient Element fracturedChild;

		/** Used to indicate when fracturing that the last leaf should be
		 * skipped. */
		transient boolean offsetLastIndex;

		/** Used to indicate that the parent of the deepest leaf should
		 * offset the index by 1 when adding/removing elements in an
		 * insert. */
		transient boolean offsetLastIndexOnReplace;

		/*
		 * Internal record used to hold element change specifications
		 */
		class ElemChanges
		{

			ElemChanges(Element parent, int index, boolean isFracture)
			{
				this.parent = parent;
				this.index = index;
				this.isFracture = isFracture;
				added = new Vector();
				removed = new Vector();
			}

			public String toString()
			{
				return "added: "+added+"\nremoved: "+removed+"\n";
			}

			Element parent;

			int index;

			Vector added;

			Vector removed;

			boolean isFracture;
		}

	}
	
	public WebEditDocument(StyleSheet ss)
	{
		super(ss);
		webuffer = new WebEditElementBuffer(createDefaultRoot());
		buffer=webuffer;
	}

  /**
	 * Initialize the document to reflect the given element structure (i.e. the
	 * structure reported by the <code>getDefaultRootElement</code> method. If
	 * the document contained any data it will first be removed.
	 */
	protected void create(ElementSpec[] data)
	{
		try
		{
			if (getLength()!=0)
			{
				remove(0, getLength());
			}
			writeLock();

			// install the content
			Content c = getContent();
			int n = data.length;
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i<n; i++)
			{
				ElementSpec es = data[i];
				if (es.getLength()>0)
				{
					sb.append(es.getArray(), es.getOffset(), es.getLength());
				}
			}
			UndoableEdit cEdit = c.insertString(0, sb.toString());

			// build the event and element structure
			int length = sb.length();
			DefaultDocumentEvent evnt = new DefaultDocumentEvent(0, length,
					DocumentEvent.EventType.INSERT);
			evnt.addEdit(cEdit);
			webuffer.create(length, data, evnt);

			// update bidi (possibly)
			super.insertUpdate(evnt, null);

			// notify the listeners
			evnt.end();
			fireInsertUpdate(evnt);
			fireUndoableEditUpdate(new UndoableEditEvent(this, evnt));
		}
		catch (BadLocationException ble)
		{
			throw new Error("problem initializing");
		}
		finally
		{
			writeUnlock();
		}

	}
	
	/*protected Element createBranchElement(Element parent, AttributeSet a)
  {
  	return new WebEditBlockElement(parent, a);
  }*/

  public HTML.Tag getParagraphTag(int offset)
	{
		Element el = super.getParagraphElement(offset);
		HTML.Tag tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		while (tag==HTML.Tag.IMPLIED)
		{
			el=el.getParentElement();
			tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		}
		return tag;
	}
	
	public Element getRealParagraphElement(int offset)
	{
		Element el = super.getParagraphElement(offset);
		HTML.Tag tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		while (tag==HTML.Tag.IMPLIED)
		{
			el=el.getParentElement();
			tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		}
		return el;
	}
	
	public void removeCharacterAttribute(int offset, int length, Object attribute)
	{
		try
		{
			writeLock();
			DefaultDocumentEvent changes = new DefaultDocumentEvent(offset, length, DocumentEvent.EventType.CHANGE);

			// split elements that need it
			buffer.change(offset, length, changes);

			int lastEnd = Integer.MAX_VALUE;
			for (int pos = offset; pos<(offset+length); pos = lastEnd)
			{
				Element run = getCharacterElement(pos);
				lastEnd = run.getEndOffset();
				if (pos==lastEnd)
				{
					// offset + length beyond length of document, bail.
					break;
				}
				MutableAttributeSet attr = (MutableAttributeSet) run.getAttributes();
				MutableAttributeSet sCopy = new SimpleAttributeSet(attr);
				sCopy.removeAttribute(attribute);
				changes.addEdit(new AttributeUndoableEdit(run, sCopy, true));
				attr.removeAttribute(attribute);
			}
			changes.end();
			fireChangedUpdate(changes);
			fireUndoableEditUpdate(new UndoableEditEvent(this, changes));
		}
		finally
		{
			writeUnlock();
		}
	}

	private void insertNewlineSpecs(List specs, List stack)
	{
		log.debug("Insert newline specs");
		Element paragraph;
		for (int j = 0; j<stack.size(); j++)
		{
			//log.debug("End tag");
			specs.add(new ElementSpec(null,ElementSpec.EndTagType));
		}
		for (int j = stack.size(); --j>=0;)
		{
			//log.debug("Start tag");
			paragraph = (Element)stack.get(j);
			specs.add(new ElementSpec(paragraph.getAttributes(),ElementSpec.StartTagType));
		}
	}
	
	protected void removeUpdate(DefaultDocumentEvent chng)
	{
		log.info("RemoveUpdate "+chng.getOffset()+" "+chng.getLength());
		super.removeUpdate(chng);
	}
	
	void updateStructure(int offset, ElementSpec[] specs)
	{
		try
		{
			writeLock();
			int length=0;
			for (int i=0; i<specs.length; i++)
			{
				length+=specs[i].getLength();
			}
	    DefaultDocumentEvent chng = new DefaultDocumentEvent(offset, length, DocumentEvent.EventType.CHANGE);
			buffer.remove(offset,length,chng);
			buffer.insert(offset,length,specs,chng);
			fireChangedUpdate(chng);
		}
		finally
		{
			writeUnlock();
		}
	}
	
  protected void insertUpdate(DefaultDocumentEvent chng, AttributeSet attr)
	{
  	log.info("InsertUpdate "+chng.getOffset()+" "+chng.getLength());
		int offset = chng.getOffset();
		int length = chng.getLength();
		List stack = new LinkedList();
		boolean fracture=false;
		Element paragraph = getParagraphElement(offset+length);
		if ((offset>paragraph.getStartOffset())||(offset==0))
		{
			log.debug("Fracturing");
			fracture=true;
		}
		stack.add(paragraph);
		HTML.Tag tag = (HTML.Tag)paragraph.getAttributes().getAttribute(StyleConstants.NameAttribute);
		while (tag==HTML.Tag.IMPLIED)
		{
			paragraph=paragraph.getParentElement();
			stack.add(paragraph);
			tag = (HTML.Tag)paragraph.getAttributes().getAttribute(StyleConstants.NameAttribute);
		}
		List specs = new LinkedList();
		
		boolean joinPrevious=true;
		Element character = getCharacterElement(offset);
		if (character.getAttributes().getAttribute(StyleConstants.NameAttribute)==HTML.Tag.IMG)
		{
			joinPrevious=false;
			log.debug("Adding to image");
		}
		
		Segment s = new Segment();
		s.setPartialReturn(false);
		ElementSpec spec;
		try
		{
			getText(offset,length,s);
			int start = s.offset;
			int end = s.count+s.offset;
			int lastend = start;
			
			if (!fracture)
			{
				insertNewlineSpecs(specs,stack);
			}
			for (int i = start; i<end; i++)
			{
				if (s.array[i]=='\n')
				{
					if (lastend<i)
					{
						log.debug("Adding content");
						spec = new ElementSpec(attr,ElementSpec.ContentType,i-lastend);
						if ((lastend==start)&&(joinPrevious))
						{
							log.debug("Joining with previous");
							spec.setDirection(ElementSpec.JoinPreviousDirection);
						}
						specs.add(spec);
					}
					log.debug("Adding newline content");
					specs.add(new ElementSpec(attr,ElementSpec.ContentType,1));
					insertNewlineSpecs(specs,stack);
					lastend=i+1;
				}
			}
			
			if (specs.size()>0)
			{
				int pos=specs.size()-1;
				spec = (ElementSpec)specs.get(pos);
				while (spec.getType()==ElementSpec.StartTagType)
				{
					if (fracture)
					{
						log.debug("Fracturing Start Tag");
						spec.setDirection(ElementSpec.JoinFractureDirection);
					}
					else
					{
						log.debug("Joining Start Tag");
						spec.setDirection(ElementSpec.JoinNextDirection);
					}
					pos--;
					spec = (ElementSpec)specs.get(pos);
				}
			}
			
			if ((lastend<end)&&((specs.size()>0)||(!joinPrevious)))
			{
				log.debug("Adding last content");
				spec = new ElementSpec(attr,ElementSpec.ContentType,end-lastend);
				spec.setDirection(ElementSpec.JoinNextDirection);
				specs.add(spec);
			}
			
			if (specs.size()>0)
			{
				ElementSpec[] results = (ElementSpec[])specs.toArray(new ElementSpec[0]);
				buffer.insert(offset,length,results,chng);
			}
		}
		catch (BadLocationException e)
		{
		}
	}
  
	public java.util.Iterator getCharacterElementIterator(int start, int end)
	{
		if (end<start)
		{
			int temp=end;
			end=start;
			start=temp;
		}
		LinkedList elements = new LinkedList();
		Element element = getCharacterElement(start);
		if (start==end)
		{
			if (element.getStartOffset()==start)
			{
				element=getCharacterElement(start-1);
			}
		}
		elements.add(element);
		while (element.getEndOffset()<end)
		{
			int pos=element.getEndOffset();
			element=getCharacterElement(pos);
			elements.add(element);
		}
		return elements.iterator();
	}
	
	public java.util.Iterator getParagraphElementIterator(int start, int end)
	{
		if (end<start)
		{
			int temp=end;
			end=start;
			start=temp;
		}
		LinkedList elements = new LinkedList();
		Element element = getRealParagraphElement(start);
		elements.add(element);
		while (element.getEndOffset()<end)
		{
			int pos=element.getEndOffset();
			element=getRealParagraphElement(pos);
			elements.add(element);
		}
		return elements.iterator();
	}
}

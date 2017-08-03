/*
 * The MIT License
 *
 * Copyright (c) 2017 Una Thompson (unascribed) and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.elytradev.mini;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import com.elytradev.mini.exception.PointerNotSetException;
import com.elytradev.mini.exception.PointerOutOfBoundsException;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class PatchContext {

	/**
	 * Represents the code found as a result of a call to search.
	 */
	public class SearchResult {

		private int start;
		private AbstractInsnNode[] query;
		private boolean reverse;
		
		protected SearchResult(int start, AbstractInsnNode[] query, boolean reverse) {
			super();
			this.start = start;
			this.query = query;
			this.reverse = reverse;
		}

		/**
		 * Check if this search was successful; i.e. if the code this search is
		 * for exists.
		 * @return {@code true} if the search was successful
		 */
		public boolean isSuccessful() {
			return start != -1;
		}
		
		/**
		 * Update the PatchContext's code pointer to just after the found code.
		 * @throws NoSuchElementException if the search was unsuccessful
		 */
		public void jumpAfter() {
			if (!isSuccessful()) throw new NoSuchElementException();
			setPointer(start+query.length);
		}
		
		/**
		 * Update the PatchContext's code pointer to just before the found code.
		 * @throws NoSuchElementException if the search was unsuccessful
		 */
		public void jumpBefore() {
			if (!isSuccessful()) throw new NoSuchElementException();
			setPointer(start-1);
		}
		
		/**
		 * Repeat this search from the end of the code found by this search.
		 * @return a new SearchResult that represents the next matching set of
		 * 		instructions
		 * @throws NoSuchElementException if the search was unsuccessful
		 */
		public SearchResult next() {
			if (!isSuccessful()) throw new NoSuchElementException();
			return searchFrom(start+query.length, query, reverse);
		}
		
		/**
		 * Erase the found code block and don't update the code pointer.
		 * @deprecated This is a compatibility nightmare and should be used sparingly.
		 * @throws NoSuchElementException if the search was unsuccessful
		 */
		@Deprecated
		public void erase() {
			if (!isSuccessful()) throw new NoSuchElementException();
			for (int i = 0; i < query.length; i++) {
				PatchContext.this.erase(start);
			}
		}
		
	}

	private final MethodNode method;
	private final List<AbstractInsnNode> code;
	private int pointer = -1;
	
	PatchContext(MethodNode method) {
		this.method = method;
		this.code = Lists.newArrayListWithCapacity(method.instructions.size());
		AbstractInsnNode ain = method.instructions.getFirst();
		Map<LabelNode, LabelNode> labels = Maps.newIdentityHashMap();
		while (ain != null) {
			if (ain instanceof LabelNode) {
				LabelNode l = new LabelNode(((LabelNode) ain).getLabel());
				labels.put((LabelNode)ain, l);
			}
			ain = ain.getNext();
		}
		ain = method.instructions.getFirst();
		while (ain != null) {
			code.add(ain.clone(labels));
			ain = ain.getNext();
		}
	}
	
	/**
	 * @return the instruction under the current code pointer
	 */
	public AbstractInsnNode get() {
		if (pointer == -1) throw new PointerNotSetException();
		return code.get(pointer);
	}
	
	public String getMethodName() {
		return method.name;
	}
	
	protected void finish() {
		method.instructions = new InsnList();
		for (AbstractInsnNode ain : code) {
			method.instructions.add(ain);
		}
	}
	
	private PointerOutOfBoundsException pointerOutOfBoundsException(int i) {
		if (i < 0) {
			return new PointerOutOfBoundsException(i+" is before the beginning of the code block");
		} else {
			return new PointerOutOfBoundsException(i+" is after the end of the code block");
		}
	}

	
	/**
	 * Inject the given bytecode at the current code pointer. The code
	 * pointer will then be updated to be immediately after the injected code.
	 * @throws PointerNotSetException if the code pointer hasn't been set
	 */
	public void add(AbstractInsnNode... nodes) {
		if (pointer == -1) throw new PointerNotSetException();
		for (int i = 0; i < nodes.length; i++) {
			code.add(pointer, nodes[i]);
			pointer++;
		}
	}
	
	/**
	 * Erase the instruction under the current pointer.
	 * @deprecated This is a compatibility nightmare and should be used sparingly.
	 */
	@Deprecated
	public void erase() {
		if (pointer == -1) throw new PointerNotSetException();
		erase(pointer);
	}
	
	private void setPointer(int pointer) {
		if (pointer < 0) throw pointerOutOfBoundsException(pointer);
		if (pointer >= method.instructions.size()) throw pointerOutOfBoundsException(pointer);
		this.pointer = pointer;
	}
	
	private void erase(int idx) {
		code.remove(idx);
	}

	private void jump(int amt) {
		if (pointer == -1) throw new PointerNotSetException();
		setPointer(pointer + amt);
	}
	
	/**
	 * Jump forward by {@code amt} instructions.
	 * @deprecated Fragile. Prefer {@link #search} when possible.
	 * @see #jumpBackward
	 * @throws PointerNotSetException if the pointer hasn't yet been set by
	 * 		{@link #jumpToEnd()}, {@link #jumpToStart()}, or {@link #search}.
	 * @throws PointerOutOfBoundsException if this jump would land the pointer
	 * 		out of the method's code block
	 */
	@Deprecated
	public void jumpForward(int amt) {
		jump(amt);
	}
	
	/**
	 * Jump backward by {@code amt} instructions.
	 * @deprecated Fragile. Prefer {@link #search} when possible.
	 * @see #jumpForward
	 * @throws PointerNotSetException if the pointer hasn't yet been set by
	 * 		{@link #jumpToEnd()}, {@link #jumpToStart()}, or {@link #search}.
	 * @throws PointerOutOfBoundsException if this jump would land the pointer
	 * 		out of the method's code block
	 */
	@Deprecated
	public void jumpBackward(int amt) {
		jump(-amt);
	}
	
	/**
	 * Update the code pointer to the start of the method.
	 */
	public void jumpToStart() {
		pointer = 0;
	}
	
	/**
	 * Update the code pointer to the end of the method.
	 */
	public void jumpToEnd() {
		pointer = method.instructions.size()-1;
	}
	
	/**
	 * Search for bytecode in the current method as defined by the given
	 * ASM objects, starting at the current code pointer, or the beginning of
	 * the method if unset. You can use ASMifier to get values to pass into
	 * this method.
	 * <p>
	 * This method searches <i>forwards</i>. The pointer will be incremented
	 * until your code block is found. To find the first occurence of something,
	 * call {@link #jumpToStart} followed by {@code search}.
	 * <p>
	 * Calling this method on its own does nothing. You must call
	 * {@link SearchResult#jumpBefore}, {@link SearchResult#jumpAfter}, or
	 * {@link SearchResult#erase} on the returned SearchResult for there to be
	 * any effect.
	 */
	public SearchResult search(AbstractInsnNode... nodes) {
		return searchFrom(pointer == -1 ? 0 : pointer, nodes, false);
	}
	
	/**
	 * Search for bytecode in the current method as defined by the given
	 * ASM objects, starting at the current code pointer, or the beginning of
	 * the method if unset. You can use ASMifier to get values to pass into
	 * this method.
	 * <p>
	 * This method searches <i>backwards</i>. The pointer will be decremented
	 * until your code block is found. To find the last occurence of something,
	 * call {@link #jumpToEnd} followed by {@code searchBackward}.
	 * <p>
	 * Calling this method on its own does nothing. You must call
	 * {@link SearchResult#jumpBefore}, {@link SearchResult#jumpAfter}, or
	 * {@link SearchResult#erase} on the returned SearchResult for there to be
	 * any effect.
	 */
	public SearchResult searchBackward(AbstractInsnNode... nodes) {
		return searchFrom(pointer == -1 ? 0 : pointer, nodes, true);
	}

	private SearchResult searchFrom(int start, AbstractInsnNode[] nodes, boolean reverse) {
		for (int k = start; reverse ? k >= 0 : k < code.size(); k += (reverse ? -1 : 1)) {
			boolean allMatched = true;
			for (int j = 0; j < nodes.length; j++) {
				if (!instructionsEqual(code.get(k+j), nodes[j])) {
					allMatched = false;
					break;
				}
			}
			if (allMatched) {
				return new SearchResult(start, nodes, reverse);
			}
		}
		return new SearchResult(-1, nodes, reverse);
	}

	private boolean instructionsEqual(AbstractInsnNode a, AbstractInsnNode b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		if (a.getClass() != b.getClass()) return false;
		if (a.getOpcode() != b.getOpcode()) return false;
		
		if (a instanceof FieldInsnNode) {
			FieldInsnNode fa = (FieldInsnNode)a;
			FieldInsnNode fb = (FieldInsnNode)b;
			return Objects.equal(fa.owner, fb.owner) &&
					Objects.equal(fa.name, fb.name) &&
					Objects.equal(fa.desc, fb.desc);
		} else if (a instanceof IincInsnNode) {
			IincInsnNode ia = (IincInsnNode)a;
			IincInsnNode ib = (IincInsnNode)b;
			return ia.var == ib.var && ia.incr == ib.incr;
		} else if (a instanceof InsnNode) {
			return true;
		} else if (a instanceof IntInsnNode) {
			IntInsnNode ia = (IntInsnNode)a;
			IntInsnNode ib = (IntInsnNode)b;
			return ia.operand == ib.operand;
		} else if (a instanceof InvokeDynamicInsnNode) {
			InvokeDynamicInsnNode ia = (InvokeDynamicInsnNode)a;
			InvokeDynamicInsnNode ib = (InvokeDynamicInsnNode)b;
			return Objects.equal(ia.bsm, ib.bsm) &&
					Arrays.equals(ia.bsmArgs, ib.bsmArgs) &&
					Objects.equal(ia.name, ib.name) &&
					Objects.equal(ia.desc, ib.desc);
		} else if (a instanceof JumpInsnNode) {
			JumpInsnNode ja = (JumpInsnNode)a;
			JumpInsnNode jb = (JumpInsnNode)b;
			return instructionsEqual(ja.label, jb.label);
		} else if (a instanceof LabelNode) {
			// no good way to compare label equality
			return true;
		} else if (a instanceof LdcInsnNode) {
			LdcInsnNode la = (LdcInsnNode)a;
			LdcInsnNode lb = (LdcInsnNode)b;
			return Objects.equal(la.cst, lb.cst);
		} else if (a instanceof LineNumberNode) {
			LineNumberNode la = (LineNumberNode)a;
			LineNumberNode lb = (LineNumberNode)b;
			return la.line == lb.line && instructionsEqual(la.start, lb.start);
		} else if (a instanceof LookupSwitchInsnNode) {
			LookupSwitchInsnNode la = (LookupSwitchInsnNode)a;
			LookupSwitchInsnNode lb = (LookupSwitchInsnNode)b;
			return instructionsEqual(la.dflt, lb.dflt) &&
					Objects.equal(la.keys, lb.keys) &&
					instructionListsEqual(la.labels, lb.labels);
		} else if (a instanceof MethodInsnNode) {
			MethodInsnNode ma = (MethodInsnNode)a;
			MethodInsnNode mb = (MethodInsnNode)b;
			return Objects.equal(ma.owner, mb.owner) &&
					Objects.equal(ma.name, mb.name) &&
					Objects.equal(ma.desc, mb.desc) &&
					ma.itf == mb.itf;
		} else if (a instanceof MultiANewArrayInsnNode) {
			MultiANewArrayInsnNode ma = (MultiANewArrayInsnNode)a;
			MultiANewArrayInsnNode mb = (MultiANewArrayInsnNode)b;
			return Objects.equal(ma.desc, mb.desc) && ma.dims == mb.dims;
		} else if (a instanceof TableSwitchInsnNode) {
			TableSwitchInsnNode ta = (TableSwitchInsnNode)a;
			TableSwitchInsnNode tb = (TableSwitchInsnNode)b;
			return ta.min == tb.min &&
					ta.max == tb.max &&
					instructionsEqual(ta.dflt, tb.dflt) &&
					instructionListsEqual(ta.labels, tb.labels);
		} else if (a instanceof TypeInsnNode) {
			TypeInsnNode ta = (TypeInsnNode)a;
			TypeInsnNode tb = (TypeInsnNode)b;
			return Objects.equal(ta.desc, tb.desc);
		} else if (a instanceof VarInsnNode) {
			VarInsnNode va = (VarInsnNode)a;
			VarInsnNode vb = (VarInsnNode)b;
			return va.var == vb.var;
		}
		throw new IllegalArgumentException("Unknown insn type "+a.getClass().getName());
	}

	private boolean instructionListsEqual(List<? extends AbstractInsnNode> a, List<? extends AbstractInsnNode> b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		if (a.size() != b.size()) return false;
		for (int i = 0; i < a.size(); i++) {
			if (!instructionsEqual(a.get(i), b.get(i))) return false;
		}
		return true;
	}

}

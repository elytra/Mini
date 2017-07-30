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

import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.elytradev.mini.annotation.Patch;
import net.minecraft.launchwrapper.IClassTransformer;

public abstract class MiniTransformer implements IClassTransformer, Opcodes {

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		boolean matched = false;
		for (Patch.Class a : getClass().getAnnotationsByType(Patch.Class.class)) {
			if (a.value().equals(transformedName)) {
				matched = true;
				break;
			}
		}
		if (!matched) return basicClass;
		
		ClassReader reader = new ClassReader(basicClass);
		ClassNode clazz = new ClassNode();
		reader.accept(clazz, 0);
		
		Method[] methods = getClass().getDeclaredMethods();
		
		for (MethodNode mn : clazz.methods) {
			for (Method m : methods) {
				for (Patch.Method a : m.getAnnotationsByType(Patch.Method.class)) {
					if (a.srg().equals(mn.name) || a.mcp().equals(mn.name)) {
						if (a.descriptor().equals(mn.desc)) {
							PatchContext ctx = new PatchContext(mn);
							try {
								m.invoke(this, ctx);
							} catch (Exception e) {
								throw new RuntimeException("Failed to invoke transformer method for "+a.mcp()+a.descriptor()+" (SRG name "+a.srg()+")", e);
							}
							ctx.finish();
							LogManager.getLogger("Mini").info("[{}] Successfully transformed {}.{}{}", getClass().getName(), transformedName, mn.name, mn.desc);
						}
					}
				}
			}
		}
		
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		clazz.accept(writer);
		return writer.toByteArray();
	}

}

package me.zioeren.jaroptimizer;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class JarOptimizer
{
	private static ArrayList<String> usedStrings = new ArrayList<String>();
	
	public static void main(String[] args) throws IOException
	{
		System.out.println("Welcome to JarOptimizer. Here you can optimize at all the performances of your jar file.");
		System.out.println("Please, put in the folder of JarOptimizer your jar file called 'input.jar'.");
		System.in.read();
		
		File inputFile = new File("input.jar");
		File outputFile = new File("output.jar");
		
		if (!inputFile.exists())
		{
			System.out.println("The input file does not exist, exiting from the application...");
			System.in.read();
			System.exit(0);
		}
		else
		{
			if (outputFile.exists())
			{
				if (!outputFile.delete())
				{
					System.out.println("Output file already exists and can't be deleted, exiting from the application...");
					System.in.read();
					System.exit(0);
				}
				else
				{
					System.out.println("Output file already exists and can't be deleted, deleting it...");
				}
			}
			
			System.out.println("The input file exist, starting the process of optimization...");
			System.out.println("The optimized file will be saved as 'output.jar' as result of the optimization.");
			
	        try (ZipOutputStream outstream = new ZipOutputStream(new FileOutputStream(outputFile)); ZipFile zipFile = new ZipFile(inputFile))
	        {
	        	Enumeration<? extends ZipEntry> enumeration = zipFile.entries();

	        	while (enumeration.hasMoreElements())
	        	{
	        		ZipEntry next = enumeration.nextElement();

	        		if (!next.isDirectory())
	        		{
	        			if (next.getName().endsWith(".class"))
	        			{
	        				byte[] classBytes = IOUtils.toByteArray(zipFile.getInputStream(next));
		        			ZipEntry result = new ZipEntry(next.getName());
		        			outstream.putNextEntry(result);
	        				
	        				try
	        				{
	        					outstream.write(transform(classBytes));
	        				}
	        				catch (Exception ex)
	        				{
	        					
	        				}
	        				
		        			outstream.closeEntry();
	        			}
	        			else
	        			{
		        			ZipEntry result = new ZipEntry(next.getName());
		        			outstream.putNextEntry(result);
	        				IOUtils.copy(zipFile.getInputStream(next), outstream);
		        			outstream.closeEntry();
	        			}
	        		}
	        		else
	        		{
	        			ZipEntry result = new ZipEntry(next.getName());
	        			outstream.putNextEntry(result);
	        			IOUtils.copy(zipFile.getInputStream(next), outstream);
	        			outstream.closeEntry();
	        		}
	        	}
	        }
	        
	        System.out.println("Optimization process succesfully finished and the output has been saved.");
	        System.out.println("Good luck with it ;)");
		}
	}
	
    private static byte[] transform(byte[] classBytes)
    {
        ClassReader reader = null;
        
        try
        {
        	reader = new ClassReader(classBytes);
        }
        catch (Exception ex)
        {
        	return null;
        }

        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        
        applyTransformations(classNode);
        
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        try
        {
            classNode.accept(writer);
        }
        catch (Exception ex)
        {
        	try
        	{
                writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                classNode.accept(writer);
        	}
        	catch (Exception ex1)
        	{
        		
        	}
        }
        
        return writer.toByteArray();
    }
    
    private static void applyTransformations(ClassNode classNode)
    {
    	classNode.methods.forEach(mn -> new ArrayList<>(mn.tryCatchBlocks).forEach(tcb -> check(classNode, mn, tcb, tcb.handler)));
    	classNode.invisibleAnnotations = null;
    	classNode.invisibleTypeAnnotations = null;
    	classNode.outerClass = null;	
    	classNode.outerMethod = null;
    	classNode.outerMethodDesc = null;
    	classNode.sourceDebug = null;
    	classNode.sourceFile = null;
    	classNode.nestMembers = null;
    	
        if (classNode.attrs != null)
        {
        	Stream.of(classNode.attrs.toArray(new Attribute[0])).filter(Attribute::isUnknown).forEach(attr ->
            {
                classNode.attrs.remove(attr);
            });
        }
        
        classNode.methods.forEach(methodNode ->
        {
            Type[] args = Type.getArgumentTypes(methodNode.desc);
            
            if (args.length > 0 && args[args.length - 1].getSort() != Type.ARRAY)
            {
                methodNode.access &= ~Opcodes.ACC_VARARGS;
            }
        });
    	
    	if (classNode.methods != null)
    	{
        	for (MethodNode methodNode: classNode.methods)
        	{
        		methodNode.localVariables = null;
        		methodNode.invisibleAnnotations = null;
        		methodNode.invisibleParameterAnnotations = null;
        		methodNode.invisibleTypeAnnotations = null;
        		methodNode.signature = null;
        		methodNode.visibleParameterAnnotations = null;
        		methodNode.visibleTypeAnnotations = null;
        		
                if (methodNode.instructions.getFirst() == null)
                {
                	continue;
                }
        		
        		methodNode.tryCatchBlocks.removeIf(tc -> (tc.handler.getNext().getOpcode() == Opcodes.INVOKESTATIC && tc.handler.getNext().getNext().getOpcode() == Opcodes.ATHROW) || tc.handler.getNext().getOpcode() == Opcodes.ATHROW);
                
                if (methodNode.tryCatchBlocks != null)
                {
                    methodNode.tryCatchBlocks.removeIf(tryCatchBlockNode -> getNext(tryCatchBlockNode.start) == getNext(tryCatchBlockNode.end));
                }
        		
        		if (methodNode.attrs != null)
        		{
                	Stream.of(methodNode.attrs.toArray(new Attribute[0])).filter(Attribute::isUnknown).forEach(attr ->
                    {
                        methodNode.attrs.remove(attr);
                    });
        		}
        		
                Stream.of(methodNode.instructions.toArray()).filter(insn -> insn instanceof LineNumberNode).forEach(insn ->
                {
                    methodNode.instructions.remove(insn);
                });
                
                Stream.of(methodNode.instructions.toArray()).filter(insn -> insn.getOpcode() == Opcodes.GOTO).forEach(insn ->
                {
                    JumpInsnNode gotoJump = (JumpInsnNode) insn;
                    AbstractInsnNode insnAfterTarget = gotoJump.label.getNext();
                    
                    if (insnAfterTarget != null && insnAfterTarget.getOpcode() == Opcodes.GOTO)
                    {
                        JumpInsnNode secGoto = (JumpInsnNode) insnAfterTarget;
                        gotoJump.label = secGoto.label;
                    }
                });
                
                Stream.of(methodNode.instructions.toArray()).filter(insn -> insn.getOpcode() == Opcodes.GOTO).forEach(insn ->
                {
                    JumpInsnNode gotoJump = (JumpInsnNode) insn;
                    AbstractInsnNode insnAfterTarget = gotoJump.label.getNext();

                    if (insnAfterTarget != null && isReturn(insnAfterTarget.getOpcode()))
                    {
                        methodNode.instructions.set(insn, new InsnNode(insnAfterTarget.getOpcode()));
                    }
                });
                
                Stream.of(methodNode.instructions.toArray()).filter(insn -> insn.getOpcode() == Opcodes.NOP).forEach(insn -> methodNode.instructions.remove(insn));
                
                Stream.of(methodNode.instructions.toArray()).forEach(insn ->
                {
                	insn.invisibleTypeAnnotations = null;
                });
                
                boolean found;

                do
                {
                    found = false;

                    for (AbstractInsnNode insnNode : methodNode.instructions.toArray())
                    {
                        if (insnNode instanceof MethodInsnNode)
                        {
                            MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                            AbstractInsnNode prev = getPrevious(methodInsnNode, 1);

                            if (prev instanceof LdcInsnNode && ((LdcInsnNode) prev).cst instanceof String && (matchMethodNode(methodInsnNode, "java/lang/Object.hashCode:()I") || matchMethodNode(methodInsnNode, "java/lang/String.hashCode:()I")))
                            {
                                methodNode.instructions.insert(insnNode, generateIntPush(((LdcInsnNode) prev).cst.hashCode()));
                                methodNode.instructions.remove(insnNode);
                                methodNode.instructions.remove(prev);
                                found = true;
                            }
                            
                            if (prev instanceof LdcInsnNode && ((LdcInsnNode) prev).cst instanceof String && (matchMethodNode(methodInsnNode, "java/lang/String.toUpperCase:()Ljava/lang/String;")))
                            {
                            	methodNode.instructions.insert(insnNode, new LdcInsnNode(((String) ((LdcInsnNode) prev).cst).toUpperCase()));
                            	methodNode.instructions.remove(insnNode);
                            	methodNode.instructions.remove(prev);
                                found = true;
                            }
                            
                            if (prev instanceof LdcInsnNode && ((LdcInsnNode) prev).cst instanceof String && (matchMethodNode(methodInsnNode, "java/lang/String.toLowerCase:()Ljava/lang/String;")))
                            {
                            	methodNode.instructions.insert(insnNode, new LdcInsnNode(((String) ((LdcInsnNode) prev).cst).toLowerCase()));
                            	methodNode.instructions.remove(insnNode);
                            	methodNode.instructions.remove(prev);
                                found = true;
                            }
                        }
                    }
                }
                while (found);
        	}	
    	}
    	
    	if (classNode.fields != null)
    	{
    		for (FieldNode fieldNode: classNode.fields)
        	{
        		fieldNode.invisibleAnnotations = null;
        		fieldNode.invisibleTypeAnnotations = null;
        		fieldNode.signature = null;
        		fieldNode.visibleTypeAnnotations = null;
        		
        		if (fieldNode.attrs != null)
        		{
                	Stream.of(fieldNode.attrs.toArray(new Attribute[0])).filter(Attribute::isUnknown).forEach(attr ->
                    {
                        fieldNode.attrs.remove(attr);
                    });
        		}
        		
        		fieldNode.visibleAnnotations = null;
        	}
    	}
    }
    
    private static boolean isInstruction(AbstractInsnNode node)
    {
        return !(node instanceof LineNumberNode) && !(node instanceof FrameNode) && !(node instanceof LabelNode);
    }
    
    public static AbstractInsnNode getNext(AbstractInsnNode node)
    {
        AbstractInsnNode next = node.getNext();
        
        while (!isInstruction(next))
        {
            next = next.getNext();
        }
        
        return next;
    }
    
	private static void check(ClassNode cn, MethodNode mn, TryCatchBlockNode tcb, LabelNode handler)
	{
		AbstractInsnNode ain = handler;
		
		while (ain.getOpcode() == -1)
		{
			ain = ain.getNext();
		}
		
		if (ain.getOpcode() == Opcodes.ATHROW)
		{
			removeTCB(mn, tcb);
		}
		else if (ain instanceof MethodInsnNode && ain.getNext().getOpcode() == Opcodes.ATHROW)
		{
			MethodInsnNode min = (MethodInsnNode) ain;
			
			if (min.owner.equals(cn.name))
			{
				MethodNode getter = getMethod(cn, min.name, min.desc);
				AbstractInsnNode getterFirst = getter.instructions.getFirst();
				
				while (getterFirst.getOpcode() == -1)
				{
					getterFirst = ain.getNext();
				}
				
				if (getterFirst instanceof VarInsnNode && getterFirst.getNext().getOpcode() == Opcodes.ARETURN)
				{
					if (((VarInsnNode) getterFirst).var == 0)
					{
						removeTCB(mn, tcb);
					}
				}
			}
		}
	}
	
	public static MethodNode getMethod(ClassNode cn, String name, String desc)
	{
		for (MethodNode mn : cn.methods)
		{
			if (mn.name.equals(name) && mn.desc.equals(desc))
			{
				return mn;
			}
		}
		
		return null;
	}

	private static void removeTCB(MethodNode mn, TryCatchBlockNode tcb)
	{
		mn.tryCatchBlocks.remove(tcb);
	}
    
    private static boolean isReturn(int opcode)
    {
        return (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN);
    }
    
    private static boolean matchMethodNode(MethodInsnNode methodInsnNode, String s)
    {
        return s.equals(methodInsnNode.owner + "." + methodInsnNode.name + ":" + methodInsnNode.desc);
    }
    
    private static AbstractInsnNode getPrevious(AbstractInsnNode node, int amount)
    {
        for (int i = 0; i < amount; i++)
        {
            node = getPrevious(node);
        }
        
        return node;
    }
    
    private static AbstractInsnNode getPrevious(AbstractInsnNode node)
    {
        AbstractInsnNode prev = node.getPrevious();
        
        while (isNotInstruction(prev))
        {
            prev = prev.getPrevious();
        }
        
        return prev;
    }
    
    private static boolean isNotInstruction(AbstractInsnNode node)
    {
        return node instanceof LineNumberNode || node instanceof FrameNode || node instanceof LabelNode;
    }
    
    public static AbstractInsnNode generateIntPush(int i)
    {
        if (i <= 5 && i >= -1)
        {
            return new InsnNode(i + 3);
        }
        
        if (i >= -128 && i <= 127)
        {
            return new IntInsnNode(Opcodes.BIPUSH, i);
        }

        if (i >= -32768 && i <= 32767)
        {
            return new IntInsnNode(Opcodes.SIPUSH, i);
        }
        
        return new LdcInsnNode(i);
    }
    
    private static final String ALPHA_NUMERIC_STRING = "abcdefghijklmno";
    
    public static String getRandomString()
    {
    	String gen = "";
    	
    	do
    	{
    		int count = 4;
        	StringBuilder builder = new StringBuilder();
        	
        	while (count-- != 0)
        	{
        		int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
        		builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        	}
        	
        	gen = builder.toString();
    	}
    	while (gen == "" || usedStrings.contains(gen));
    	
    	usedStrings.add(gen);
    	return gen;
    }
    
    public static boolean isTerminating(AbstractInsnNode next)
    {
        switch (next.getOpcode())
        {
            case Opcodes.RETURN:
            case Opcodes.ARETURN:
            case Opcodes.IRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.LRETURN:
            case Opcodes.ATHROW:
            case Opcodes.TABLESWITCH:
            case Opcodes.LOOKUPSWITCH:
            case Opcodes.GOTO:
                return true;
        }
        
        return false;
    }
    
	public static boolean willPushToStack(int opcode)
    {
        switch (opcode)
        {
            case Opcodes.ACONST_NULL:
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
            case Opcodes.LDC:
            case Opcodes.GETSTATIC:
            case Opcodes.ILOAD:
            case Opcodes.LLOAD:
            case Opcodes.FLOAD:
            case Opcodes.DLOAD:
            case Opcodes.ALOAD:
            {
                return true;
            }
        }
        
        return false;
    }
    
    public static int iconstToInt(int opcode)
    {
        int operand = Integer.MIN_VALUE;
        
        switch (opcode)
        {
            case Opcodes.ICONST_0:
                operand = 0;
                break;
            case Opcodes.ICONST_1:
                operand = 1;
                break;
            case Opcodes.ICONST_2:
                operand = 2;
                break;
            case Opcodes.ICONST_3:
                operand = 3;
                break;
            case Opcodes.ICONST_4:
                operand = 4;
                break;
            case Opcodes.ICONST_5:
                operand = 5;
                break;
            case Opcodes.ICONST_M1:
                operand = -1;
                break;
        }
        
        return operand;
    }
    
    public static AbstractInsnNode getNextFollowGoto(AbstractInsnNode node)
    {
        AbstractInsnNode next = node.getNext();
        
        while (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode)
        {
            next = next.getNext();
        }
        
        if (next.getOpcode() == Opcodes.GOTO)
        {
            JumpInsnNode cast = (JumpInsnNode) next;
            next = cast.label;
            
            while (!isInstruction(next))
            {
                next = next.getNext();
            }
        }
        
        return next;
    }
}
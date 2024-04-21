package dev.nonamecrackers2.simpleclouds.client.renderer;

import java.io.IOException;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import dev.nonamecrackers2.simpleclouds.SimpleCloudsMod;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.AtomicCounter;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.BufferObject;
import dev.nonamecrackers2.simpleclouds.client.shader.compute.ComputeShader;
import dev.nonamecrackers2.simpleclouds.common.noise.AbstractNoiseSettings;
import dev.nonamecrackers2.simpleclouds.common.noise.NoiseSettings;
import dev.nonamecrackers2.simpleclouds.common.noise.StaticNoiseSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

public class CloudMeshGenerator implements AutoCloseable
{
	public static final int MAX_NOISE_LAYERS = 4;
	private static final ResourceLocation CUBE_MESH_GENERATOR = SimpleCloudsMod.id("cube_mesh");
	private static final Logger LOGGER = LogManager.getLogger("simpleclouds/CloudMeshGenerator");
	public static final int WORK_X = 64;
	public static final int WORK_Y = 16;
	public static final int WORK_Z = 64;
	public static final int LOCAL_X = 8;
	public static final int LOCAL_Y = 8;
	public static final int LOCAL_Z = 8;
	private @Nullable ComputeShader shader;
	private float scrollX;
	private float scrollY;
	private float scrollZ;
	
	protected CloudMeshGenerator() {}
	
	public static int getCloudAreaMaxRadius()
	{
		int width = WORK_X * LOCAL_X;
		int length = WORK_Z * LOCAL_Z;
		return width / 2 + length / 2;
	}
	
	public static int getCloudRenderDistance()
	{
		return Math.max(WORK_X * LOCAL_X, WORK_Z * LOCAL_Z) / 2;
	}
	
	@Override
	public void close()
	{
		if (this.shader != null)
			this.shader.close();
	}
	
	public void init(ResourceManager manager)
	{
		if (this.shader != null)
		{
			this.shader.close();
			this.shader = null;
		}
		
		try
		{
			this.shader = ComputeShader.loadShader(CUBE_MESH_GENERATOR, manager, LOCAL_X, LOCAL_Y, LOCAL_Z);
			this.shader.bindAtomicCounter(0, GL15.GL_DYNAMIC_DRAW); //Counter
			this.shader.bindShaderStorageBuffer(1, GL15.GL_DYNAMIC_DRAW).allocateBuffer(368435456); //Vertex data, arbitrary size
			this.shader.bindShaderStorageBuffer(2, GL15.GL_DYNAMIC_DRAW).allocateBuffer(107108864); //Index data, arbitrary size
			this.shader.bindShaderStorageBuffer(3, GL15.GL_STATIC_DRAW).allocateBuffer(AbstractNoiseSettings.Param.values().length * 4 * MAX_NOISE_LAYERS);
			this.generateMesh(StaticNoiseSettings.DEFAULT, false, 0.0F, 0.0F, 0.0F, 0.5F, 1.0F);
		}
		catch (IOException e)
		{
			LOGGER.warn("Failed to load compute shader", e);
		}
	}
	
	public void setScroll(float x, float y, float z)
	{
		this.scrollX = x;
		this.scrollY = y;
		this.scrollZ = z;
	}
	
	public void generateMesh(NoiseSettings settings, boolean addMovementSmoothing, double camX, double camY, double camZ, float threshold, float scale)
	{
		this.generateMesh(settings, addMovementSmoothing, camX, camY, camZ, threshold, scale, false);
	}
	
	public void generateMesh(NoiseSettings settings, boolean addMovementSmoothing, double camX, double camY, double camZ, float threshold, float scale, boolean wait)
	{
		int count = Math.min(settings.layerCount(), MAX_NOISE_LAYERS);
		
		this.shader.<AtomicCounter>getBufferObject(0).set(0);
		this.shader.forUniform("LayerCount", loc -> {
			GL20.glUniform1i(loc, count);
		});
		this.shader.<BufferObject>getBufferObject(3).writeData(b -> 
		{
			float[] packed = settings.packForShader();
			for (int i = 0; i < packed.length && i < AbstractNoiseSettings.Param.values().length * MAX_NOISE_LAYERS; i++)
				b.putFloat(i * 4, packed[i]);
		});
		float cloudCenterOffsetX = WORK_X * LOCAL_X / 2.0F * scale;
		float cloudCenterOffsetZ = WORK_Z * LOCAL_Z / 2.0F * scale;
		float offsetX = ((float)Mth.floor(camX / 16.0D) * 16.0F - cloudCenterOffsetX) / scale;
		float offsetZ = ((float)Mth.floor(camZ / 16.0D) * 16.0F - cloudCenterOffsetZ) / scale;
		this.shader.forUniform("RenderOffset", loc -> {
			GL20.glUniform2f(loc, offsetX, offsetZ);
		});
		this.shader.forUniform("Scroll", loc -> {
			GL20.glUniform3f(loc, this.scrollX, this.scrollY, this.scrollZ);
		});
		this.shader.dispatch(WORK_X, WORK_Y, WORK_Z, wait);
	}
	
	public @Nullable ComputeShader getShader()
	{
		return this.shader;
	}
}

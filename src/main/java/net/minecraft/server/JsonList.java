package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.koloboke.collect.map.hash.HashObjObjMaps;
import com.mojang.authlib.GameProfile;

import io.akarin.server.core.AkarinAsyncExecutor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;

public class JsonList<K, V extends JsonListEntry<K>> {

    protected static final Logger a = LogManager.getLogger();
    protected final Gson b;
    private final File c;
    // Paper - replace HashMap is ConcurrentHashMap
    protected Map<String, V> d = Collections.emptyMap(); private final Map<String, V> getBackingMap() { return this.d; } // Paper - OBFHELPER // Akarin
    private boolean e = true;
    private static final ParameterizedType f = new ParameterizedType() {
        public Type[] getActualTypeArguments() {
            return new Type[] { JsonListEntry.class};
        }

        public Type getRawType() {
            return List.class;
        }

        public Type getOwnerType() {
            return null;
        }
    };

    public JsonList(File file) {
        this.c = file;
        GsonBuilder gsonbuilder = (new GsonBuilder()).setPrettyPrinting();

        gsonbuilder.registerTypeHierarchyAdapter(JsonListEntry.class, new JsonList.JsonListEntrySerializer());
        this.b = gsonbuilder.create();
    }

    public boolean isEnabled() {
        return this.e;
    }

    public void setEnabled(boolean flag) { a(flag); } // Paper - OBFHeLPER
    public void a(boolean flag) {
        this.e = flag;
    }

    public File c() {
        return this.c;
    }

    public void add(V v0) {
        // Akarin start
        Map<String, V> toImmutable = HashObjObjMaps.newUpdatableMap(this.getBackingMap());
        toImmutable.put(this.a(v0.getKey()), v0);
        this.d = HashObjObjMaps.newImmutableMap(toImmutable);
        // Akarin end

        try {
            this.save();
        } catch (IOException ioexception) {
            JsonList.a.warn("Could not save the list after adding a user.", ioexception);
        }

    }

    @Nullable
    public V get(K k0) {
        // Paper start
        // this.h();
        // return (V) this.d.get(this.a(k0)); // CraftBukkit - fix decompile error
        return (V) this.getBackingMap().computeIfPresent(this.getMappingKey(k0), (k, v) -> {
            return v.hasExpired() ? null : v;
        });
        // Paper end
    }

    public void remove(K k0) {
        // Akarin start
        Map<String, V> toImmutable = HashObjObjMaps.newMutableMap(this.getBackingMap());
        toImmutable.remove(this.a(k0));
        this.d = HashObjObjMaps.newImmutableMap(toImmutable);
        // Akarin end

        try {
            this.save();
        } catch (IOException ioexception) {
            JsonList.a.warn("Could not save the list after removing a user.", ioexception);
        }

    }

    public void b(JsonListEntry<K> jsonlistentry) {
        this.remove(jsonlistentry.getKey());
    }

    public String[] getEntries() {
        return (String[]) this.d.keySet().toArray(new String[this.d.size()]);
    }

    // CraftBukkit start
    public Collection<V> getValues() {
        return this.d.values();
    }
    // CraftBukkit end

    public boolean isEmpty() {
        // return this.d.size() < 1; // Paper
        return this.getBackingMap().isEmpty(); // Paper - readability is the goal. As an aside, isEmpty() uses only sumCount() and a comparison. size() uses sumCount(), casts, and boolean logic
    }

    protected final String getMappingKey(K k0) { return a(k0); } // Paper - OBFHELPER
    protected String a(K k0) {
        return k0.toString();
    }

    protected boolean d(K k0) {
        return this.d.containsKey(this.a(k0));
    }

    private void removeStaleEntries() { h(); } // Paper - OBFHELPER
    private void h() {
        /*List<K> list = Lists.newArrayList();
        Iterator iterator = this.d.values().iterator();

        while (iterator.hasNext()) {
            V v0 = (V) iterator.next(); // CraftBukkit - decompile error

            if (v0.hasExpired()) {
                list.add(v0.getKey());
            }
        }

        iterator = list.iterator();

        while (iterator.hasNext()) {
            K k0 = (K) iterator.next(); // CraftBukkit - decompile error

            this.d.remove(this.a(k0));
        }*/

        // Akarin start
        Map<String, V> toImmutable = HashObjObjMaps.newMutableMap(this.getBackingMap());
        toImmutable.values().removeIf(JsonListEntry::hasExpired);
        this.d = HashObjObjMaps.newImmutableMap(toImmutable);
        // Akarin end
        // Paper end
    }

    protected JsonListEntry<K> a(JsonObject jsonobject) {
        return new JsonListEntry<>((K) null, jsonobject); // CraftBukkit - decompile error
    }

    public Collection<V> e() {
        return this.d.values();
    }

    public void save() throws IOException {
        this.removeStaleEntries(); // Paper - remove expired values before saving
        Runnable runnable = () -> { // Akarin
        Collection<V> collection = this.d.values();
        String s = this.b.toJson(collection);
        BufferedWriter bufferedwriter = null;

        try {
            bufferedwriter = Files.newWriter(this.c, StandardCharsets.UTF_8);
            bufferedwriter.write(s);
        } catch (IOException e) { // Akarin
            Bukkit.getLogger().log(Level.SEVERE, "Failed to save {0}, {1}", new Object[] {this.c.getName(), e.getMessage()}); // Akarin
        } finally {
            IOUtils.closeQuietly(bufferedwriter);
        }
        }; // Akarin
        AkarinAsyncExecutor.scheduleSingleAsyncTask(runnable); // Akarin

    }

    public void load() throws FileNotFoundException {
        if (this.c.exists()) {
            BufferedReader bufferedreader = null;

            try {
                bufferedreader = Files.newReader(this.c, StandardCharsets.UTF_8);
                Collection<JsonListEntry<K>> collection = (Collection) ChatDeserializer.a(this.b, (Reader) bufferedreader, (Type) JsonList.f);

                if (collection != null) {
                    Iterator iterator = collection.iterator();

                    Map<String, V> toImmutable = HashObjObjMaps.newUpdatableMap(this.getBackingMap()); // Akarin
                    while (iterator.hasNext()) {
                        JsonListEntry<K> jsonlistentry = (JsonListEntry) iterator.next();

                        if (jsonlistentry.getKey() != null) {
                            toImmutable.put(this.a((K) jsonlistentry.getKey()), (V) jsonlistentry); // CraftBukkit - fix decompile error // Akarin
                        }
                    }
                    this.d = HashObjObjMaps.newImmutableMap(toImmutable); // Akarin
                }
            // Spigot Start
            } catch ( com.google.gson.JsonParseException ex )
            {
                org.bukkit.Bukkit.getLogger().log( java.util.logging.Level.WARNING, "Unable to read file " + this.c + ", backing it up to {0}.backup and creating new copy.", ex );
                File backup = new File( this.c + ".backup" );
                this.c.renameTo( backup );
                this.c.delete();
            // Spigot End
            } finally {
                IOUtils.closeQuietly(bufferedreader);
            }

        }
    }

    class JsonListEntrySerializer implements JsonDeserializer<JsonListEntry<K>>, JsonSerializer<JsonListEntry<K>> {

        private JsonListEntrySerializer() {}

        public JsonElement serialize(JsonListEntry<K> jsonlistentry, Type type, JsonSerializationContext jsonserializationcontext) {
            JsonObject jsonobject = new JsonObject();

            jsonlistentry.a(jsonobject);
            return jsonobject;
        }

        public JsonListEntry<K> deserialize(JsonElement jsonelement, Type type, JsonDeserializationContext jsondeserializationcontext) throws JsonParseException {
            if (jsonelement.isJsonObject()) {
                JsonObject jsonobject = jsonelement.getAsJsonObject();

                return JsonList.this.a(jsonobject);
            } else {
                return null;
            }
        }
    }
}

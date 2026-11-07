package net.kasax.questsicons.compat.mixin;

import net.kasax.questsicons.compat.QiListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

/**
 * Makes {@link ListTag} implement {@link QiListTag}: lists made with {@link QiListTag#mixed()} accept elements of
 * different types, and a list that really holds different types is written like 1.21.5+ does (as a list of
 * compounds, each non-compound element wrapped as {@code {"": value}}; read back with MixedLists.unwrap).
 */
@Mixin(ListTag.class)
public abstract class ListTagMixin implements QiListTag {
    @Shadow
    private byte type;
    @Shadow
    @org.spongepowered.asm.mixin.Final
    private List<Tag> list;

    @Unique
    private boolean questsicons$mixed;

    @Override
    public void questsicons$allowMixed() {
        questsicons$mixed = true;
    }

    @Inject(method = "updateType", at = @At("HEAD"), cancellable = true)
    private void questsicons$acceptMixed(Tag tag, CallbackInfoReturnable<Boolean> cir) {
        if (questsicons$mixed && tag.getId() != Tag.TAG_END && this.type != Tag.TAG_END && this.type != tag.getId()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void questsicons$writeMixed(DataOutput out, CallbackInfo ci) throws IOException {
        boolean mixed = false;
        for (Tag t : this.list) {
            if (t.getId() != this.list.get(0).getId()) {
                mixed = true;
                break;
            }
        }
        if (!mixed) return;
        out.writeByte(Tag.TAG_COMPOUND);
        out.writeInt(this.list.size());
        for (Tag t : this.list) {
            if (t instanceof CompoundTag c && !(c.size() == 1 && c.contains(""))) {
                c.write(out);
            } else {
                CompoundTag w = new CompoundTag();
                w.put("", t);
                w.write(out);
            }
        }
        ci.cancel();
    }
}

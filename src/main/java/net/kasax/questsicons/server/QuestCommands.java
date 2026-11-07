package net.kasax.questsicons.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.kasax.questsicons.item.ModItems;
import net.kasax.questsicons.progress.QuestSavedData;
import net.kasax.questsicons.progress.TeamData;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestObject;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** {@code /quests ...}: open the book, admin tools and simple teams. */
public final class QuestCommands {
    private QuestCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> QUESTS = (ctx, b) -> {
        QuestServer qs = QuestServer.get();
        List<String> ids = new ArrayList<>();
        if (qs != null) for (Quest q : qs.book.allQuests()) ids.add(q.idString());
        ids.add("all");
        return SharedSuggestionProvider.suggest(ids, b);
    };

    /** Called from NeoForge's RegisterCommandsEvent (see QuestsIcons). */
    public static void init(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher);
    }

    private static QuestServer qs() {
        QuestServer q = QuestServer.get();
        if (q == null) throw new IllegalStateException("Quest server not running");
        return q;
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        var root = Commands.literal("quests")
                .then(Commands.literal("open").executes(c -> {
                    qs().openBook(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
                    qs().load();
                    c.getSource().sendSuccess(() -> Component.translatable("questsicons.command.reloaded", qs().book.chapters.size()), true);
                    return 1;
                }))
                .then(Commands.literal("givebook").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                            for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
                                ItemStack s = new ItemStack(ModItems.QUEST_BOOK);
                                if (!p.getInventory().add(s)) p.drop(s, false);
                            }
                            return 1;
                        })))
                .then(Commands.literal("complete").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("quest", StringArgumentType.word()).suggests(QUESTS)
                                .executes(c -> complete(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> complete(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("quest", StringArgumentType.word()).suggests(QUESTS)
                                .executes(c -> reset(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> reset(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(FtbImportCommand.node())
                .then(Commands.literal("team")
                        .then(Commands.literal("info").executes(QuestCommands::teamInfo))
                        .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString()).executes(QuestCommands::teamCreate)))
                        .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(QuestCommands::teamInvite)))
                        .then(Commands.literal("join").then(Commands.argument("owner", EntityArgument.player()).executes(QuestCommands::teamJoin)))
                        .then(Commands.literal("leave").executes(QuestCommands::teamLeave)));
        d.register(root);
        d.register(Commands.literal("questbook").redirect(d.getRoot().getChild("quests")));
    }

    private static int complete(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        String id = StringArgumentType.getString(c, "quest");
        QuestServer qs = qs();
        int n = 0;
        for (ServerPlayer p : players) {
            TeamData team = qs.team(p);
            if (id.equals("all")) {
                for (Quest q : qs.book.allQuests()) qs.forceComplete(team, q, p);
            } else {
                Quest q = qs.book.quest(QuestObject.parseId(id));
                if (q == null) {
                    c.getSource().sendFailure(Component.translatable("questsicons.command.unknown_quest", id));
                    return 0;
                }
                qs.forceComplete(team, q, p);
            }
            n++;
        }
        int done = n;
        c.getSource().sendSuccess(() -> Component.translatable("questsicons.command.completed", done), true);
        return n;
    }

    private static int reset(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        String id = StringArgumentType.getString(c, "quest");
        QuestServer qs = qs();
        for (ServerPlayer p : players) {
            Quest q = id.equals("all") ? null : qs.book.quest(QuestObject.parseId(id));
            if (!id.equals("all") && q == null) {
                c.getSource().sendFailure(Component.translatable("questsicons.command.unknown_quest", id));
                return 0;
            }
            qs.reset(qs.team(p), q);
        }
        c.getSource().sendSuccess(() -> Component.translatable("questsicons.command.reset", players.size()), true);
        return players.size();
    }

    private static int teamInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        TeamData t = qs().team(p);
        c.getSource().sendSuccess(() -> Component.translatable("questsicons.team.info", t.name, String.join(", ", t.memberNames.values()),
                t.completed.size()).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int teamCreate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        QuestServer qs = qs();
        QuestSavedData data = qs.data();
        TeamData current = qs.team(p);
        String name = StringArgumentType.getString(c, "name");
        if (current.members.size() == 1 && current.owner.equals(p.getUUID())) {
            current.name = name;
        } else {
            leave(qs, p, current);
            TeamData t = data.teamOf(p.getUUID(), p.getGameProfile().getName());
            t.name = name;
        }
        qs.markDirty(qs.team(p));
        c.getSource().sendSuccess(() -> Component.translatable("questsicons.team.created", name), false);
        return 1;
    }

    private static int teamInvite(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        TeamData t = qs().team(p);
        t.invites.add(target.getUUID());
        qs().data().setDirty();
        String join = "/quests team join " + p.getGameProfile().getName();
        target.sendSystemMessage(Component.translatable("questsicons.team.invited", p.getDisplayName(), t.name, p.getGameProfile().getName())
                .withStyle(s -> s.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, join))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(join)))));
        c.getSource().sendSuccess(() -> Component.translatable("questsicons.team.invite_sent", target.getDisplayName()), false);
        return 1;
    }

    private static int teamJoin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerPlayer owner = EntityArgument.getPlayer(c, "owner");
        QuestServer qs = qs();
        TeamData target = qs.team(owner);
        if (!target.invites.contains(p.getUUID())) {
            c.getSource().sendFailure(Component.translatable("questsicons.team.not_invited"));
            return 0;
        }
        TeamData current = qs.team(p);
        if (current == target) return 0;
        // Only leave the old team here: leave() would also make a fresh personal team that kept the player as a
        // member, and after a restart the player ended up back in that empty team instead of the one they joined.
        removeFromTeam(qs, p, current);
        target.invites.remove(p.getUUID());
        target.members.add(p.getUUID());
        target.memberNames.put(p.getUUID(), p.getGameProfile().getName());
        target.gotBook.add(p.getUUID());           // they already got their book; without this the next login gave a second one
        qs.data().playerTeam.put(p.getUUID(), target.id);
        qs.markDirty(target);
        qs.completeTaskless(target, p);
        c.getSource().sendSuccess(() -> Component.translatable("questsicons.team.joined", target.name), false);
        return 1;
    }

    private static int teamLeave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        QuestServer qs = qs();
        TeamData current = qs.team(p);
        if (current.members.size() <= 1) {
            c.getSource().sendFailure(Component.translatable("questsicons.team.alone"));
            return 0;
        }
        leave(qs, p, current);
        qs.markDirty(qs.team(p));
        c.getSource().sendSuccess(() -> Component.translatable("questsicons.team.left"), false);
        return 1;
    }

    /** Takes the player out of a team (a team nobody is left in is deleted). */
    private static void removeFromTeam(QuestServer qs, ServerPlayer p, TeamData team) {
        UUID u = p.getUUID();
        team.members.remove(u);
        team.memberNames.remove(u);
        if (team.members.isEmpty()) {
            qs.data().teams.remove(team.id);
        } else {
            if (team.owner.equals(u)) team.owner = team.members.iterator().next();
            qs.markDirty(team);
        }
        qs.data().playerTeam.remove(u);
        qs.data().setDirty();
    }

    /** Removes the player from a shared team; they get a fresh personal team. */
    private static void leave(QuestServer qs, ServerPlayer p, TeamData team) {
        UUID u = p.getUUID();
        removeFromTeam(qs, p, team);
        TeamData fresh = new TeamData(UUID.randomUUID(), p.getGameProfile().getName(), u);
        fresh.members.add(u);
        fresh.memberNames.put(u, p.getGameProfile().getName());
        fresh.gotBook.add(u);
        qs.data().teams.put(fresh.id, fresh);
        qs.data().playerTeam.put(u, fresh.id);
        qs.data().setDirty();
    }
}

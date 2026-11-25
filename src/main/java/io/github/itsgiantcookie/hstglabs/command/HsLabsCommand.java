package io.github.itsgiantcookie.hstglabs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.core.ResearchBook;
import io.github.itsgiantcookie.hstglabs.core.tech.Problem;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import io.github.itsgiantcookie.hstglabs.tech.TechRepository;
import io.github.itsgiantcookie.hstglabs.tech.Technology;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /hslabs} (docs/design.md §13 WP-11, pulled forward so the basic lab can be playtested before
 * the lab screen exists): list technologies, and show, add to or remove from your research queue.
 * Adding goes through the same gate as the screen will (queue_permission, HS prerequisites and
 * requirements, §4.3). English text until WP-21 adds lang keys.
 */
public final class HsLabsCommand {

    private static final SuggestionProvider<CommandSourceStack> TECHNOLOGIES = (ctx, builder) ->
          SharedSuggestionProvider.suggestResource(TechReloader.current().technologies().keySet(), builder);

    private HsLabsCommand() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> register(e.getDispatcher()));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hslabs")
              .then(Commands.literal("tech").then(Commands.literal("list").executes(HsLabsCommand::techList)))
              .then(Commands.literal("queue")
                    .then(Commands.literal("list").executes(HsLabsCommand::queueList))
                    .then(Commands.literal("add").then(Commands.argument("technology", ResourceLocationArgument.id()).suggests(TECHNOLOGIES)
                          .executes(HsLabsCommand::queueAdd)))
                    .then(Commands.literal("remove").then(Commands.argument("technology", ResourceLocationArgument.id()).suggests(TECHNOLOGIES)
                          .executes(HsLabsCommand::queueRemove)))));
    }

    private static int techList(CommandContext<CommandSourceStack> ctx) {
        TechRepository.Snapshot snapshot = TechReloader.current();
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal(snapshot.technologies().size() + " technologies enabled, "
              + snapshot.definitions().size() + " defined, " + snapshot.errors() + " errors").withStyle(ChatFormatting.GOLD), false);
        for (Technology t : snapshot.technologies().values()) {
            source.sendSuccess(() -> Component.literal(" " + t.id() + "  stage " + t.stage() + ", " + t.cost().units() + " × "
                  + t.cost().unitTime() + " ticks, tier " + t.minTier()), false);
        }
        List<Problem> errors = snapshot.problems().stream().filter(p -> p.severity() == Problem.Severity.ERROR).limit(5).toList();
        for (Problem p : errors) {
            source.sendSuccess(() -> Component.literal(" ✗ " + p.subject() + ": " + p.message()).withStyle(ChatFormatting.RED), false);
        }
        if (snapshot.technologies().isEmpty()) {
            source.sendSuccess(() -> Component.literal(" Add technologies in config/hstg_labs/technologies/ and run /reload.")
                  .withStyle(ChatFormatting.GRAY), false);
        }
        return snapshot.technologies().size();
    }

    private static int queueList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResearchManager m = manager(ctx);
        OwnerId owner = m.ownerOf(player);
        ResearchBook<ResourceLocation> book = m.book(owner);
        ctx.getSource().sendSuccess(() -> Component.literal("Research queue (" + book.queue().size() + "/" + book.queue().max() + ")")
              .withStyle(ChatFormatting.GOLD), false);
        boolean first = true;
        for (ResourceLocation tech : book.queue().entries()) {
            String progress = book.progress(tech).map(p -> p.done() + "/" + p.units() + (p.inFlight() > 0 ? " (+" + p.inFlight() + " running)" : ""))
                  .orElse("0");
            String head = first ? " ▶ " : "   ";
            first = false;
            ctx.getSource().sendSuccess(() -> Component.literal(head + tech + "  " + progress), false);
        }
        for (ResourceLocation tech : book.blockedTechnologies()) {
            ctx.getSource().sendSuccess(() -> Component.literal(" ⛔ " + tech + "  blocked by History Stages; add it again to retry")
                  .withStyle(ChatFormatting.RED), false);
        }
        return book.queue().size();
    }

    private static int queueAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation tech = ResourceLocationArgument.getId(ctx, "technology");
        ResearchManager.EnqueueResult result = manager(ctx).enqueueBy(player, tech);
        String message = switch (result) {
            case ADDED -> "Queued " + tech;
            case DUPLICATE -> tech + " is already queued";
            case FULL -> "The queue is full";
            case UNKNOWN_TECHNOLOGY -> "No enabled technology " + tech + " (see /hslabs tech list)";
            case ALREADY_RESEARCHED -> tech + " is already researched";
            case VETOED -> "Another mod refused to queue " + tech;
            case NO_PERMISSION -> "Only operators may edit research queues on this server";
            case PREREQUISITES_MISSING -> "Research the prerequisites of " + tech + " first";
            case REQUIREMENTS_UNMET -> "The requirements of " + tech + "'s stage are not met yet";
        };
        if (result == ResearchManager.EnqueueResult.ADDED) {
            ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }

    private static int queueRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation tech = ResourceLocationArgument.getId(ctx, "technology");
        ResearchManager m = manager(ctx);
        if (m.dequeue(m.ownerOf(player), tech)) {
            ctx.getSource().sendSuccess(() -> Component.literal("Removed " + tech + " from the queue").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(tech + " is not queued"));
        return 0;
    }

    private static ResearchManager manager(CommandContext<CommandSourceStack> ctx) {
        return ResearchManager.get().orElseThrow(() -> new IllegalStateException("research is not running"));
    }
}

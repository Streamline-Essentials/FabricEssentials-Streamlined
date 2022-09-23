package org.server_utilities.essentials.command.impl.teleportation.tpa;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.server_utilities.essentials.command.Command;
import org.server_utilities.essentials.util.TpaManager;

public class TpaCommand extends Command {

    private static final String TARGET_ARGUMENT_ID = "target";
    private final TpaManager.Direction direction;

    public static final TpaCommand TPA = new TpaCommand(TpaManager.Direction.THERE);
    public static final TpaCommand TPA_HERE = new TpaCommand(TpaManager.Direction.HERE);

    private TpaCommand(TpaManager.Direction direction) {
        super(direction.getProperties());
        this.direction = direction;
    }

    @Override
    protected void register(LiteralArgumentBuilder<CommandSourceStack> literal) {
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> target = Commands.argument(TARGET_ARGUMENT_ID, EntityArgument.player());
        target.executes(this::execute);
        literal.then(target);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, TARGET_ARGUMENT_ID);
        TpaManager.Participants participants = new TpaManager.Participants(ctx.getSource().getPlayerOrException().getUUID(), target.getUUID());
        TpaManager.Direction direction = TpaManager.INSTANCE.getRequest(participants);
        if (direction == this.direction) {
            sendFailure(ctx.getSource(), "pending");
            return FAILURE;
        }
        TpaManager.INSTANCE.addRequest(participants, this.direction);
        sendSuccess(ctx.getSource(), "self", target.getDisplayName());
        sendVictimMessage(ctx.getSource().getPlayerOrException(), target);
        return SUCCESS;
    }

    public void sendVictimMessage(ServerPlayer source, ServerPlayer target) {
        sendSuccess(target.createCommandSourceStack(), "victim",
                source.getDisplayName(),
                Component.translatable(TPA.translation("accept"))
                        .withStyle(style ->
                                style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept %s".formatted(source.getScoreboardName())))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(TPA.translation("accept.hover"))))
                        )
        );
    }

}

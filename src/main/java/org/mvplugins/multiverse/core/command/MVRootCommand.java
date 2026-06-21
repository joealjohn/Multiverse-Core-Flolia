package org.mvplugins.multiverse.core.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.BukkitCommandManager;
import co.aikar.commands.BukkitRootCommand;
import co.aikar.commands.CommandIssuer;
import org.mvplugins.multiverse.core.utils.StringFormatter;

import java.util.List;

public class MVRootCommand extends BukkitRootCommand {
    protected MVRootCommand(BukkitCommandManager manager, String name) {
        super(manager, name);
    }

    @Override
    public BaseCommand execute(CommandIssuer sender, String commandLabel, String[] args) {
        String[] quoteFormatedArgs = StringFormatter.parseQuotesInArgs(args).toArray(String[]::new);
        if (org.mvplugins.multiverse.core.folia.FoliaDetector.isFolia() && !org.mvplugins.multiverse.core.folia.FoliaDetector.isGlobalTickThread()) {
            org.mvplugins.multiverse.core.folia.FoliaSchedulerAdapter.runGlobalTask(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("Multiverse-Core"),
                    () -> super.execute(sender, commandLabel, quoteFormatedArgs)
            );
            return null;
        }
        return super.execute(sender, commandLabel, quoteFormatedArgs);
    }

    @Override
    public List<String> getTabCompletions(CommandIssuer sender, String alias, String[] args, boolean commandsOnly, boolean isAsync) {
        String[] quoteFormatedArgs = StringFormatter.parseQuotesInArgs(args).toArray(String[]::new);
        return super.getTabCompletions(sender, alias, quoteFormatedArgs, commandsOnly, isAsync)
                .stream()
                .map(StringFormatter::quoteMultiWordString)
                .toList();
    }
}

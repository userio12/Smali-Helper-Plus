package com.riyadm.smalihelperplus.core.smali

/** Built-in, original smali samples used as starters and quick examples (all pass validation). */
object SmaliSamples {
    data class Sample(val id: String, val title: String, val code: String)

    val STARTER = """
        .class public Lcom/acme/demo/Greeter;
        .super Ljava/lang/Object;
        .source "Greeter.java"

        # instance fields
        .field private count:I

        # direct methods
        .method public constructor <init>()V
            .registers 1

            invoke-direct {p0}, Ljava/lang/Object;-><init>()V

            return-void
        .end method

        # virtual methods
        .method public greet(Ljava/lang/String;)Ljava/lang/String;
            .registers 5
            .param p1, "name" # Ljava/lang/String;

            iget v0, p0, Lcom/acme/demo/Greeter;->count:I

            add-int/lit8 v0, v0, 0x1

            iput v0, p0, Lcom/acme/demo/Greeter;->count:I

            if-nez p1, :cond_0

            const-string p1, "stranger"

            :cond_0
            new-instance v1, Ljava/lang/StringBuilder;

            invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

            const-string v2, "Hello, "

            invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

            invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

            invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

            move-result-object v1

            return-object v1
        .end method
    """.trimIndent() + "\n"

    val ARITHMETIC = """
        .class public final Lcom/acme/demo/MathUtils;
        .super Ljava/lang/Object;

        .method public static max(II)I
            .registers 2

            if-lt p0, p1, :cond_0

            return p0

            :cond_0
            return p1
        .end method

        .method public static average(JJ)J
            .registers 8

            add-long v0, p0, p2

            const-wide/16 v2, 0x2

            div-long/2addr v0, v2

            return-wide v0
        .end method
    """.trimIndent() + "\n"

    val LOOP = """
        .class public Lcom/acme/demo/Loops;
        .super Ljava/lang/Object;

        .method public static sum([I)I
            .registers 5
            .param p0, "values" # [I

            const/4 v0, 0x0

            const/4 v1, 0x0

            :goto_0
            array-length v2, p0

            if-ge v1, v2, :cond_0

            aget v3, p0, v1

            add-int/2addr v0, v3

            add-int/lit8 v1, v1, 0x1

            goto :goto_0

            :cond_0
            return v0
        .end method
    """.trimIndent() + "\n"

    val SWITCH = """
        .class public Lcom/acme/demo/Days;
        .super Ljava/lang/Object;

        .method public static name(I)Ljava/lang/String;
            .registers 2

            packed-switch p0, :pswitch_data_0

            const-string v0, "Unknown"

            return-object v0

            :pswitch_0
            const-string v0, "Monday"

            return-object v0

            :pswitch_1
            const-string v0, "Tuesday"

            return-object v0

            :pswitch_data_0
            .packed-switch 0x1
                :pswitch_0
                :pswitch_1
            .end packed-switch
        .end method
    """.trimIndent() + "\n"

    val TRY_CATCH = """
        .class public Lcom/acme/demo/Numbers;
        .super Ljava/lang/Object;

        .method public static safeParse(Ljava/lang/String;)I
            .registers 3

            :try_start_0
            invoke-static {p0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

            move-result v0
            :try_end_0
            .catch Ljava/lang/NumberFormatException; {:try_start_0 .. :try_end_0} :catch_0

            return v0

            :catch_0
            move-exception v1

            const/4 v0, -0x1

            return v0
        .end method
    """.trimIndent() + "\n"

    val SNIPPET = """
        const/4 v0, 0x1
        const/16 v1, 0x64
        add-int v2, v0, v1
        mul-int/lit8 v2, v2, 0x2
    """.trimIndent() + "\n"

    val all: List<Sample> = listOf(
        Sample("starter", "Greeter class", STARTER),
        Sample("snippet", "Constants & math", SNIPPET),
        Sample("arithmetic", "Max & average", ARITHMETIC),
        Sample("loop", "Array loop", LOOP),
        Sample("switch", "Packed switch", SWITCH),
        Sample("try_catch", "Try / catch", TRY_CATCH),
    )
}

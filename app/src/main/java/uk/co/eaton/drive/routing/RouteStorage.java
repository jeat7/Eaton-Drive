package uk.co.eaton.drive.routing;

import android.content.Context;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Atomic local hand-off; avoids sending large route point arrays via Android Binder. */
public final class RouteStorage {
    private static final int MAGIC = 0x45445256; // EDRV
    private static final int VERSION = 1;
    private RouteStorage() {}
    private static File path(Context context) {
        return new File(context.getFilesDir(), "navigation-route.bin");
    }
    public static void save(Context context, RouteParser.Route route, RouteOptions options)
            throws IOException {
        if (route == null || route.points.size()<2 || route.points.size()>75000
            || route.turns.size()>10000) throw new IOException("Invalid route size");
        File output = path(context), temporary = new File(context.getFilesDir(),"navigation-route.tmp");
        try {
            try(FileOutputStream stream = new FileOutputStream(temporary);
                DataOutputStream data = new DataOutputStream(new BufferedOutputStream(stream))) {
                data.writeInt(MAGIC); data.writeInt(VERSION);
                data.writeBoolean(options.avoidMotorways);
                data.writeBoolean(options.avoidTolls);
                data.writeInt(options.alternativeIndex);
                data.writeInt(route.estimatedSeconds);
                data.writeInt(route.points.size());
                for(RouteParser.Point point:route.points) {
                    data.writeDouble(point.lat); data.writeDouble(point.lon);
                }
                data.writeInt(route.turns.size());
                for(RouteParser.Turn turn:route.turns) {
                    data.writeDouble(turn.point.lat); data.writeDouble(turn.point.lon);
                    data.writeInt(turn.routeIndex);
                    data.writeUTF(turn.code==null?"":turn.code);
                }
                data.flush();
                stream.getFD().sync();
            }
            if(output.exists()&&!output.delete())throw new IOException("Cannot replace navigation route");
            if(!temporary.renameTo(output))throw new IOException("Cannot activate navigation route");
        } finally {
            if(temporary.exists())temporary.delete();
        }
    }
    public static final class Saved {
        public final RouteParser.Route route;
        public final RouteOptions options;
        Saved(RouteParser.Route route,RouteOptions options){this.route=route;this.options=options;}
    }
    public static Saved load(Context context)throws IOException {
        File file=path(context);
        if(!file.isFile()||file.length()>15_000_000L)throw new IOException("Saved route unavailable");
        try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Incompatible route format");
            boolean motorways=in.readBoolean(),tolls=in.readBoolean();
            int alternative=in.readInt(), seconds=in.readInt();
            if(alternative<0||alternative>1||seconds<0||seconds>7*86400)throw new IOException("Invalid routing preferences");
            int count=in.readInt();
            if(count<2||count>75000)throw new IOException("Route point count invalid");
            List<RouteParser.Point> points=new ArrayList<>(count);
            double miles=0;
            for(int i=0;i<count;i++){
                RouteParser.Point p=new RouteParser.Point(in.readDouble(),in.readDouble());
                if(!points.isEmpty())miles+=RouteParser.distanceMiles(points.get(i-1),p);
                points.add(p);
            }
            int turns=in.readInt();
            if(turns<0||turns>10000)throw new IOException("Turn count invalid");
            List<RouteParser.Turn> instructions=new ArrayList<>(turns);
            for(int i=0;i<turns;i++){
                RouteParser.Point p=new RouteParser.Point(in.readDouble(),in.readDouble());
                int idx=in.readInt();String code=in.readUTF();
                if(idx<0||idx>=count||code.length()>60)throw new IOException("Invalid turn");
                instructions.add(new RouteParser.Turn(p,code,idx));
            }
            return new Saved(new RouteParser.Route(points,instructions,miles,seconds),
                new RouteOptions(motorways,tolls,alternative));
        }catch(IllegalArgumentException invalid){
            throw new IOException("Corrupt route coordinates or settings",invalid);
        }
    }
    public static void clear(Context context){path(context).delete();}
}

package com.example.pecanventures.bluetoothdiscoveryexample;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class BLEDeviceAdapter extends RecyclerView.Adapter<BLEDeviceAdapter.ViewHolder> {

    private List<BLEDeviceModel> devices;
    private Context ctx;
    private ItemOnClickListener listener;

    public BLEDeviceAdapter(Context ctx, List<BLEDeviceModel> devices, ItemOnClickListener listener) {
//        this.devices = devices;
        this.ctx = ctx;
        this.listener = listener;
        this.devices = new ArrayList<BLEDeviceModel>();
        this.devices.addAll(devices);
    }

    @Override
    public BLEDeviceAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        View contactView = inflater.inflate(R.layout.item_list_device, parent, false);

        final ViewHolder viewHolder = new ViewHolder(contactView);
        contactView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               listener.onItemClick(v, viewHolder.getLayoutPosition());
            }
        });
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(BLEDeviceAdapter.ViewHolder viewHolder, int position) {
        BLEDeviceModel item = devices.get(position);

        viewHolder.name.setText(ctx.getString(R.string.device_name, item.getName()));
        viewHolder.address.setText(ctx.getString(R.string.device_address, item.getAddress()));
    }



    @Override
    public int getItemCount() {
        return devices.size();
    }


public class ViewHolder extends RecyclerView.ViewHolder {
    public TextView name;
    public TextView address;

    public ViewHolder(View itemView) {
        super(itemView);

        name = (TextView) itemView.findViewById(R.id.name);
        address = (TextView) itemView.findViewById(R.id.address);
    }

}

    public interface ItemOnClickListener {
        public void onItemClick(View v, int position);
    }

    public void updateData(List<BLEDeviceModel> input) {
        if (devices != null) {
            devices.clear();
        }
//        notifyDataSetChanged();
        if (devices == null) {
            devices = input;
        } else {
            devices.addAll(input);
        }
        notifyDataSetChanged();
    }



}

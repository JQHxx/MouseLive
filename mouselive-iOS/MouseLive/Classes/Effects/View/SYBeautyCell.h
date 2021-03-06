//
//  SYBeautyCell.h
//  MouseLive
//
//  Created by GasparChu on 2020/4/16.
//  Copyright © 2020 sy. All rights reserved.
//

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface SYBeautyCell : UICollectionViewCell

/// 设置名称和缩略图
/// @param name name description
/// @param thumb thumb description
- (void)setName:(NSString * _Nullable)name
          thumb:(NSString * _Nullable)thumb
       selected:(BOOL)selected;

@end

NS_ASSUME_NONNULL_END
